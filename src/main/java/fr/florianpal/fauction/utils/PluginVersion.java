package fr.florianpal.fauction.utils;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version of a release, compared numerically component by component : a comparison of strings would
 * put {@code 1.9.10} before {@code 1.9.9}, and such a tag does exist.
 */
public record PluginVersion(int major, int minor, int patch) implements Comparable<PluginVersion> {

    /**
     * {@code V_2.2.2}, {@code v2.2.2}, {@code 2.2.2} or {@code V2.2}. Anchored on both sides, so any
     * suffix ({@code -SNAPSHOT}, {@code -beta}, {@code -rc1}...) is refused.
     */
    private static final Pattern RELEASE = Pattern.compile("^(?:[vV]_?)?(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?$");

    private static final Comparator<PluginVersion> ORDER = Comparator.comparingInt(PluginVersion::major)
            .thenComparingInt(PluginVersion::minor)
            .thenComparingInt(PluginVersion::patch);

    /**
     * Parses a release version, a missing component counting as 0. Anything else, a development build
     * included, gives an empty result instead of throwing.
     */
    public static Optional<PluginVersion> parseRelease(String raw) {

        if (raw == null) {
            return Optional.empty();
        }

        Matcher matcher = RELEASE.matcher(raw.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new PluginVersion(
                    Integer.parseInt(matcher.group(1)),
                    component(matcher.group(2)),
                    component(matcher.group(3))
            ));
        } catch (NumberFormatException e) {
            // A component too long for an int.
            return Optional.empty();
        }
    }

    private static int component(String group) {
        return group == null ? 0 : Integer.parseInt(group);
    }

    @Override
    public int compareTo(PluginVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
