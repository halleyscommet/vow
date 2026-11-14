package us.dingl.VowSMPPlugin.Items.Abilities;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffectType;
import us.dingl.VowSMPPlugin.Items.Durations;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// one value an admin fills in when adding an ability, like a cooldown length. Values are stored on the
/// item as text, in the form {@link Type#check} turns them into.
public record AbilitySetting(String key, String label, Type type, String defaultValue) {

    public enum Type {
        /// "10m", "1h30m", "45s" - stored as whole seconds
        DURATION,
        /// a whole number
        NUMBER,
        /// any text
        TEXT,
        /// potion effects with levels, one per line or comma separated: "speed 2, night_vision" -
        /// stored as "speed:2,night_vision:1"
        EFFECTS;

        /// the value to store, or null if the input doesn't make sense for this type
        public String check(String input) {
            String trimmed = input == null ? "" : input.trim();
            return switch (this) {
                case DURATION -> {
                    long seconds = Durations.parseSeconds(trimmed);
                    yield seconds > 0 ? Long.toString(seconds) : null;
                }
                case NUMBER -> {
                    try {
                        yield Long.toString(Long.parseLong(trimmed));
                    } catch (NumberFormatException e) {
                        yield null;
                    }
                }
                case TEXT -> trimmed;
                case EFFECTS -> {
                    Map<PotionEffectType, Integer> effects = parseEffectInput(trimmed);
                    if (effects == null || effects.isEmpty()) yield null;
                    List<String> parts = new ArrayList<>();
                    effects.forEach((type, level) -> parts.add(effectName(type) + ":" + level));
                    yield String.join(",", parts);
                }
            };
        }

        /// typed effects -> level per effect, or null if any of them doesn't make sense
        private static Map<PotionEffectType, Integer> parseEffectInput(String input) {
            Map<PotionEffectType, Integer> effects = new LinkedHashMap<>();
            for (String entry : input.split("[,\\n]")) {
                String[] parts = entry.trim().split("\\s+");
                if (parts[0].isEmpty()) continue;
                PotionEffectType type = effectType(parts[0]);
                if (type == null || parts.length > 2) return null;
                int level = 1;
                if (parts.length == 2) {
                    try {
                        level = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                if (level < 1 || level > 255) return null;
                effects.put(type, level);
            }
            return effects;
        }

        /// a stored EFFECTS value -> level per effect. Anything unknown (like a removed effect) is skipped.
        public static Map<PotionEffectType, Integer> parseEffects(String stored) {
            Map<PotionEffectType, Integer> effects = new LinkedHashMap<>();
            if (stored == null) return effects;
            for (String entry : stored.split(",")) {
                String[] parts = entry.split(":");
                PotionEffectType type = effectType(parts[0]);
                if (type == null) continue;
                try {
                    effects.put(type, parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
                } catch (NumberFormatException ignored) {
                }
            }
            return effects;
        }

        private static PotionEffectType effectType(String name) {
            NamespacedKey key = NamespacedKey.fromString(name.toLowerCase());
            return key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT).get(key);
        }

        /// "speed" for vanilla effects, the full key for anything else
        private static String effectName(PotionEffectType type) {
            NamespacedKey key = type.getKey();
            return key.getNamespace().equals(NamespacedKey.MINECRAFT) ? key.getKey() : key.toString();
        }

        /// a stored value, written the way an admin would type it
        public String display(String stored) {
            if (this == DURATION) {
                try {
                    return Durations.format(Long.parseLong(stored));
                } catch (NumberFormatException e) {
                    return stored;
                }
            }
            if (this == EFFECTS) {
                List<String> parts = new ArrayList<>();
                parseEffects(stored).forEach((type, level) -> parts.add(effectName(type) + " " + level));
                return String.join(", ", parts);
            }
            return stored;
        }

        public String hint() {
            return switch (this) {
                case DURATION -> "like 10m, 1h30m or 45s";
                case NUMBER -> "a whole number";
                case TEXT -> "any text";
                case EFFECTS -> "like speed 2, strength 1, night_vision";
            };
        }
    }
}
