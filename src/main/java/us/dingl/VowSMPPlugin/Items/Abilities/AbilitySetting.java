package us.dingl.VowSMPPlugin.Items.Abilities;

import us.dingl.VowSMPPlugin.Items.Durations;

/// one value an admin fills in when adding an ability, like a cooldown length. Values are stored on the
/// item as text, in the form {@link Type#check} turns them into.
public record AbilitySetting(String key, String label, Type type, String defaultValue) {

    public enum Type {
        /// "10m", "1h30m", "45s" - stored as whole seconds
        DURATION,
        /// a whole number
        NUMBER,
        /// any text
        TEXT;

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
            };
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
            return stored;
        }

        public String hint() {
            return switch (this) {
                case DURATION -> "like 10m, 1h30m or 45s";
                case NUMBER -> "a whole number";
                case TEXT -> "any text";
            };
        }
    }
}
