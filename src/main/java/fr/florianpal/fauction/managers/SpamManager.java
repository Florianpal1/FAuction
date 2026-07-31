package fr.florianpal.fauction.managers;

import fr.florianpal.fauction.FAuction;
import fr.florianpal.fauction.configurations.GlobalConfig;
import fr.florianpal.fauction.enums.SpamAction;
import fr.florianpal.fauction.languages.MessageKeys;
import fr.florianpal.fauction.objects.SpamLimit;
import fr.florianpal.fauction.objects.TokenBucket;
import fr.florianpal.fauction.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protection against packet spamming.
 * <p>
 * Each player owns one token bucket per {@link SpamAction} : a bucket starts full, every action
 * consumes one token and the bucket refills at a fixed rate. A human clicking by bursts stays inside
 * the burst allowance, while a client sending dozens of packets per second empties the bucket
 * immediately and gets throttled to the refill rate.
 */
public class SpamManager implements Listener {

    public static final String BYPASS_PERMISSION = "fauction.bypass.spam";

    /**
     * Players inactive during that delay are removed from the memory (ms).
     */
    private static final long IDLE_EXPIRATION = 300_000L;

    /**
     * Two blocked actions further apart than this delay do not belong to the same burst (ms).
     */
    private static final long STREAK_RESET = 1_000L;

    /**
     * Minimum delay between two console warnings about the same player (ms).
     */
    private static final long LOG_COOLDOWN = 60_000L;

    /**
     * Purge interval of the inactive players (ticks).
     */
    private static final long PURGE_INTERVAL = 6_000L;

    private final FAuction plugin;

    private final GlobalConfig globalConfig;

    private final Map<UUID, PlayerRate> rates = new ConcurrentHashMap<>();

    /**
     * Origin of the monotonic clock, so the protection is not affected by a system time change.
     */
    private final long startNano = System.nanoTime();

    public SpamManager(FAuction plugin) {
        this.plugin = plugin;
        this.globalConfig = plugin.getConfigurationManager().getGlobalConfig();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::purge, PURGE_INTERVAL, PURGE_INTERVAL);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        rates.remove(e.getPlayer().getUniqueId());
    }

    /**
     * @return true if the action must be cancelled.
     */
    public boolean spamTest(Player player) {
        return spamTest(player, SpamAction.INTERACT);
    }

    /**
     * @return true if the action must be cancelled.
     */
    public boolean spamTest(Player player, SpamAction action) {

        if (!globalConfig.isSecurityForSpammingPacket() || player.hasPermission(BYPASS_PERMISSION)) {
            return false;
        }

        SpamLimit limit = globalConfig.getSpamLimit(action);
        long now = now();
        PlayerRate rate = rates.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerRate(now));
        rate.lastActivity = now;

        if (rate.bucket(action, limit, now).tryConsume(limit, now)) {
            return false;
        }

        onBlocked(player, rate, now);
        return true;
    }

    private void onBlocked(Player player, PlayerRate rate, long now) {

        if (now - rate.lastBlock > STREAK_RESET) {
            rate.blockedStreak = 0;
        }
        rate.lastBlock = now;
        rate.blockedStreak++;

        if (now - rate.lastMessage >= globalConfig.getSpamMessageCooldown()) {
            rate.lastMessage = now;
            MessageUtil.sendMessage(plugin, player, MessageKeys.SPAM);
        }

        // Only a client sending packets faster than a human can click reaches that streak, so an
        // accidental double click never pollutes the console.
        int logThreshold = globalConfig.getSpamLogThreshold();
        if (logThreshold > 0 && rate.blockedStreak >= logThreshold && now - rate.lastLog >= LOG_COOLDOWN) {
            rate.lastLog = now;
            plugin.getLogger().warning("Warning : Spam gui auction Pseudo : " + player.getName() + ", " + rate.blockedStreak + " actions blocked in a row");
        }
    }

    private void purge() {
        long now = now();
        rates.entrySet().removeIf(entry -> now - entry.getValue().lastActivity > IDLE_EXPIRATION);
    }

    private long now() {
        return (System.nanoTime() - startNano) / 1_000_000L;
    }

    private static final class PlayerRate {

        private final Map<SpamAction, TokenBucket> buckets = new EnumMap<>(SpamAction.class);

        private long lastActivity;

        private long lastBlock = Long.MIN_VALUE / 4;

        private long lastMessage = Long.MIN_VALUE / 4;

        private long lastLog = Long.MIN_VALUE / 4;

        private int blockedStreak;

        private PlayerRate(long now) {
            this.lastActivity = now;
        }

        private TokenBucket bucket(SpamAction action, SpamLimit limit, long now) {
            return buckets.computeIfAbsent(action, a -> new TokenBucket(limit, now));
        }
    }
}
