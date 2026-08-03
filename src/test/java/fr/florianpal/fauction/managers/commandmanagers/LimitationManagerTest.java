package fr.florianpal.fauction.managers.commandmanagers;

import fr.florianpal.fauction.FAuctionTestBase;
import fr.florianpal.fauction.managers.VaultIntegrationManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LimitationManagerTest extends FAuctionTestBase {

    private LimitationManager limitationManager;

    private Player player;

    @BeforeEach
    void setUpLimitationManager() {

        VaultIntegrationManager vaultIntegrationManager = mock(VaultIntegrationManager.class);
        when(vaultIntegrationManager.getPerms()).thenReturn(null);
        when(plugin.getVaultIntegrationManager()).thenReturn(vaultIntegrationManager);

        limitationManager = new LimitationManager(plugin);
        player = server.addPlayer();
    }

    @Test
    @DisplayName("An empty limitations map denies selling instead of throwing")
    void emptyLimitationsMapDeniesByDefault() {

        when(globalConfig.getLimitations()).thenReturn(Map.of());

        assertEquals(0, limitationManager.getAuctionLimitationByConfig(player));
    }

    @Test
    @DisplayName("The configured default limit is used when no permission group matches")
    void configuredDefaultIsUsed() {

        when(globalConfig.getLimitations()).thenReturn(Map.of("default", 5));

        assertEquals(5, limitationManager.getAuctionLimitationByConfig(player));
    }
}