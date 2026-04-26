package taxi.sim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/**
 * RIDE_HAIL_PRHS regulatory operating window (DESIGN.md §2.9).
 *
 * <p>A window has:
 * <ul>
 *   <li>A {@code [startSeconds, endSeconds)} interval within a day (00:00 = 0,
 *       24:00 = 86400). Cross-midnight windows are not supported (split them).
 *   <li>A {@link Day} set indicating which days of the week the window applies.
 * </ul>
 *
 * <p>Spec format: {@code "HH:MM-HH:MM,DAYS"} where DAYS is a comma list of
 * day tokens or a range like {@code "Mon-Fri"}. Examples:
 * <pre>
 *   "07:00-10:00,Mon-Fri"
 *   "16:00-19:00,Fri,Sat"
 *   "00:00-04:00,Sat"
 *   "10:00-13:00,Sun"
 * </pre>
 *
 * <p>Used by {@link ShiftSimulator} to gate {@link TaxiType#RIDE_HAIL_PRHS}
 * trip generation. Outside any active window for the simulation's day of
 * week (per {@code sim.day.of.week} config), PRHS taxis remain OFF_DUTY.
 */
final class PrhsWindow {

    enum Day {
        MON, TUE, WED, THU, FRI, SAT, SUN;

        static Day parse(String token) {
            switch (token.trim().toUpperCase()) {
                case "MON": return MON;
                case "TUE": return TUE;
                case "WED": return WED;
                case "THU": return THU;
                case "FRI": return FRI;
                case "SAT": return SAT;
                case "SUN": return SUN;
                default:
                    throw new IllegalArgumentException("Unknown day token: " + token);
            }
        }
    }

    final long startSeconds;  // inclusive, seconds since midnight
    final long endSeconds;    // exclusive
    final EnumSet<Day> days;
    final String rawSpec;

    private PrhsWindow(long startSeconds, long endSeconds, EnumSet<Day> days, String rawSpec) {
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
        this.days = days;
        this.rawSpec = rawSpec;
    }

    /**
     * Parse a single window spec (e.g. {@code "07:00-10:00,Mon-Fri"}).
     *
     * @throws IllegalArgumentException if the spec is malformed
     */
    static PrhsWindow parse(String spec) {
        String[] parts = spec.split(",", 2);
        if (parts.length < 2) {
            throw new IllegalArgumentException("PrhsWindow spec missing day list: " + spec);
        }
        String[] timeRange = parts[0].split("-", 2);
        if (timeRange.length != 2) {
            throw new IllegalArgumentException("PrhsWindow spec missing time range: " + spec);
        }
        long startSec = parseHHMM(timeRange[0].trim());
        long endSec = parseHHMM(timeRange[1].trim());
        if (endSec <= startSec) {
            throw new IllegalArgumentException("PrhsWindow end must be after start: " + spec);
        }
        EnumSet<Day> days = parseDays(parts[1].trim());
        return new PrhsWindow(startSec, endSec, days, spec);
    }

    private static long parseHHMM(String hhmm) {
        String[] parts = hhmm.split(":", 2);
        int hours = Integer.parseInt(parts[0]);
        int minutes = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return (hours * 60L + minutes) * 60L;
    }

    private static EnumSet<Day> parseDays(String dayList) {
        EnumSet<Day> result = EnumSet.noneOf(Day.class);
        for (String token : dayList.split(",")) {
            token = token.trim();
            if (token.contains("-")) {
                // Range: Mon-Fri
                String[] range = token.split("-", 2);
                Day start = Day.parse(range[0]);
                Day end = Day.parse(range[1]);
                int s = start.ordinal();
                int e = end.ordinal();
                if (e < s) {
                    throw new IllegalArgumentException("Day range out of order: " + token);
                }
                for (int i = s; i <= e; i++) {
                    result.add(Day.values()[i]);
                }
            } else {
                result.add(Day.parse(token));
            }
        }
        return result;
    }

    /** True if this window is active on the given day. */
    boolean appliesOn(Day day) {
        return days.contains(day);
    }

    /** True if {@code timeSeconds} (since midnight) is inside the window. */
    boolean contains(long timeSeconds) {
        return timeSeconds >= startSeconds && timeSeconds < endSeconds;
    }

    /**
     * Parse a list of window spec strings (typically from
     * {@link TaxiConfig#getPrhsWindowSpecs()}) and filter to those active on
     * the given day. Returns an unmodifiable list ordered by start time
     * (helpful for "next window" queries).
     */
    static List<PrhsWindow> parseAndFilter(List<String> specs, Day forDay) {
        List<PrhsWindow> all = new ArrayList<>();
        for (String spec : specs) {
            PrhsWindow w = parse(spec);
            if (w.appliesOn(forDay)) {
                all.add(w);
            }
        }
        all.sort((a, b) -> Long.compare(a.startSeconds, b.startSeconds));
        return all;
    }

    /**
     * Find the window currently active for {@code timeSeconds}, or the next
     * future window in the sorted list. Returns null if no future window
     * exists today.
     *
     * @param windows sorted by startSeconds ascending (typically from
     *                {@link #parseAndFilter})
     * @param timeSeconds current shift time in seconds since midnight
     */
    static PrhsWindow findCurrentOrNext(List<PrhsWindow> windows, long timeSeconds) {
        for (PrhsWindow w : windows) {
            if (w.contains(timeSeconds) || w.startSeconds > timeSeconds) {
                return w;
            }
        }
        return null;  // no future window today
    }

    @Override
    public String toString() {
        return rawSpec + " (" + Arrays.toString(days.toArray()) + ")";
    }
}
