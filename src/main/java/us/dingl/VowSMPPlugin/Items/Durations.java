package us.dingl.VowSMPPlugin.Items;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// reads and writes durations like "1h30m", "10m" or "45s". A bare number means seconds.
public final class Durations {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*([dhms])");

    private Durations() {}

    /// whole seconds, or -1 if it isn't a duration
    public static long parseSeconds(String input) {
        String text = input.trim().toLowerCase().replace(" ", "");
        if (text.isEmpty()) return -1;
        if (text.chars().allMatch(Character::isDigit)) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        Matcher matcher = PART.matcher(text);
        long total = 0;
        int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) return -1; // junk between parts
            long amount = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2)) {
                case "d" -> amount * 86400;
                case "h" -> amount * 3600;
                case "m" -> amount * 60;
                default -> amount;
            };
            end = matcher.end();
        }
        return end == text.length() ? total : -1;
    }

    /// 5400 -> "1h30m"
    public static String format(long seconds) {
        if (seconds <= 0) return "0s";
        StringBuilder out = new StringBuilder();
        long[] sizes = {86400, 3600, 60, 1};
        char[] units = {'d', 'h', 'm', 's'};
        for (int i = 0; i < sizes.length; i++) {
            long amount = seconds / sizes[i];
            if (amount > 0) {
                out.append(amount).append(units[i]);
                seconds %= sizes[i];
            }
        }
        return out.toString();
    }
}
