package app.retrogram.graph;

/**
 * Daily download window arithmetic (WP 6.5), pure Java so it is unit-tested on the JVM. Hours are 0..23; the window runs from
 * {@code startHour:00} up to but not including {@code endHour:00} and may wrap midnight; equal hours mean "all day".
 */
public final class DownloadWindow {

    private DownloadWindow() {}

    public static boolean contains(int minuteOfDay, int startHour, int endHour) {
        if (startHour == endHour) {
            return true;
        }
        final int start = startHour * 60;
        final int end = endHour * 60;
        return start < end ? (minuteOfDay >= start && minuteOfDay < end) : (minuteOfDay >= start || minuteOfDay < end);
    }

    /** Milliseconds until the window next opens, 0 if it is open now. {@code secondOfMinute} is the current seconds for a precise delay. */
    public static long millisUntilOpen(int minuteOfDay, int secondOfMinute, int startHour, int endHour) {
        if (contains(minuteOfDay, startHour, endHour)) {
            return 0;
        }
        final int start = startHour * 60;
        int minutes = start - minuteOfDay;
        if (minutes < 0) {
            minutes += 24 * 60;
        }
        return minutes * 60_000L - secondOfMinute * 1000L;
    }
}
