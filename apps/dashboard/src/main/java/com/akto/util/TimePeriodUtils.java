package com.akto.util;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

public class TimePeriodUtils {
    private static final LoggerMaker loggerMaker = new LoggerMaker(TimePeriodUtils.class, LogDb.DASHBOARD);

    public static long getBucketSize(long totalDays) {
        if (totalDays <= 7) {
            return 1;      
        } else if (totalDays <= 60) {
            return 7;      
        } else {
            return 10;     
        }
    }

    public static Map<String, Long> groupByTimePeriod(List<Integer> timestamps, long daysBetween) {
        Map<String, Long> result = new HashMap<>();
        for (Integer timestamp : timestamps) {
            if (timestamp != null && timestamp > 0) {
                String key = getTimePeriodKey(timestamp, daysBetween);
                result.put(key, result.getOrDefault(key, 0L) + 1);
            }
        }
        return result;
    }

    public static String getTimePeriodKey(int timestamp, long daysBetween) {
        LocalDate date = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();

        if (daysBetween <= 15) {
            // Day grouping: return "D_YYYY-MM-DD" to explicitly indicate day type
            return "D_" + date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } else if (daysBetween <= 105) {
            // Week grouping: return "W_YYYY_W" to explicitly indicate week type
            WeekFields weekFields = WeekFields.of(Locale.getDefault());
            int week = date.get(weekFields.weekOfWeekBasedYear());
            int year = date.get(weekFields.weekBasedYear());
            return "W_" + year + "_" + week;
        } else {
            // Month grouping: return "M_YYYY_M" to explicitly indicate month type
            int year = date.getYear();
            int month = date.getMonthValue();
            return "M_" + year + "_" + month;
        }
    }


    public static List<List<Object>> convertTimePeriodMapToTimeSeriesData(
            Map<String, Long> timePeriodMap,
            long daysBetween
    ) {
        List<List<Object>> result = new ArrayList<>();

        // Convert to list and sort by actual timestamp value
        List<Map.Entry<String, Long>> sortedEntries = timePeriodMap.entrySet().stream()
                .sorted((e1, e2) -> {
                    long ts1 = convertTimePeriodKeyToTimestamp(e1.getKey(), daysBetween);
                    long ts2 = convertTimePeriodKeyToTimestamp(e2.getKey(), daysBetween);
                    return Long.compare(ts1, ts2);
                })
                .collect(Collectors.toList());

        for (Map.Entry<String, Long> entry : sortedEntries) {
            String timePeriodKey = entry.getKey();
            Long count = entry.getValue();

            // Convert time period key back to timestamp
            long timestamp = convertTimePeriodKeyToTimestamp(timePeriodKey, daysBetween);

            // Return [timestamp_in_milliseconds, count]
            List<Object> dataPoint = new ArrayList<>();
            dataPoint.add(timestamp * 1000L); // Convert to milliseconds
            dataPoint.add(count);
            result.add(dataPoint);
        }

        return result;
    }

    public static long convertTimePeriodKeyToTimestamp(String timePeriodKey, long daysBetween) {
        try {
            if (timePeriodKey.startsWith("D_")) {
                // Day format: "D_YYYY-MM-DD"
                String dateStr = timePeriodKey.substring(2);
                LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                return date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
            } else if (timePeriodKey.startsWith("W_")) {
                // Week format: "W_YYYY_W"
                String[] parts = timePeriodKey.substring(2).split("_");
                int year = Integer.parseInt(parts[0]);
                int week = Integer.parseInt(parts[1]);
                WeekFields weekFields = WeekFields.of(Locale.getDefault());
                LocalDate date = LocalDate.of(year, 1, 1)
                    .with(weekFields.weekBasedYear(), year)
                    .with(weekFields.weekOfWeekBasedYear(), week);
                return date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
            } else if (timePeriodKey.startsWith("M_")) {
                // Month format: "M_YYYY_M"
                String[] parts = timePeriodKey.substring(2).split("_");
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                LocalDate date = LocalDate.of(year, month, 1);
                return date.atStartOfDay(ZoneId.systemDefault()).toEpochSecond();
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error converting time period key to timestamp: " + e.getMessage());
        }
        return 0;
    }

    // ---------------------------------------------------------------------------------------------
    // Auto bucket interval ("nice intervals")
    //
    // Use this for any time-series chart that must stay readable for ANY selected date range, instead
    // of hand-picking day/week/month thresholds per chart. It is the standard approach behind Grafana
    // and Kibana "auto interval" and Elasticsearch's auto_date_histogram:
    //   1. raw interval = range length / target number of bars
    //   2. round it UP to the next size on a fixed ladder of calendar-friendly intervals
    //      (1 day, 2 days, 1 week, 2 weeks, 1 month, 3 months, 1 year)
    //   3. align bars to the calendar in the viewer's time zone (local midnight, Monday, 1st of month)
    //
    // With a target of 30 bars:
    //   7 days -> 7 daily bars          32 days -> 16 two-day bars     90 days -> 13 weekly bars
    //   1 year -> 27 two-week bars      2 years -> 25 monthly bars     3 years -> 13 quarterly bars
    //
    // Typical use:
    //   ZoneId zone = TimePeriodUtils.zoneOrDefault(timeZoneSentByTheBrowser);
    //   BucketInterval interval = TimePeriodUtils.autoInterval(startTs, endTs, 30);
    //   List<Long> starts = TimePeriodUtils.bucketStarts(startTs, endTs, interval, zone);
    //   long[] counts = TimePeriodUtils.countPerBucket(rows, Row::getTimestamp, row -> true, starts, endTs);
    //
    // Unlike groupByTimePeriod above, every bar in the range is returned, empty ones as 0, so a chart
    // never silently drops quiet days. All timestamps are epoch seconds.
    // ---------------------------------------------------------------------------------------------

    /** One rung of the interval ladder: "amount" calendar units per bar, e.g. 2 x DAYS. */
    public static final class BucketInterval {
        private final ChronoUnit unit;
        private final int amount;
        private final long approxSeconds;
        private final String title;

        private BucketInterval(ChronoUnit unit, int amount, long approxSeconds, String title) {
            this.unit = unit;
            this.amount = amount;
            this.approxSeconds = approxSeconds;
            this.title = title;
        }

        public ChronoUnit getUnit() { return unit; }
        public int getAmount() { return amount; }
        /** Approximate length in seconds (a month counts as 30 days); only used to pick the rung. */
        public long getApproxSeconds() { return approxSeconds; }
        /** Human label for the bar size, e.g. "Daily", "Every 2 weeks", "Monthly". */
        public String getTitle() { return title; }
    }

    private static final long DAY_SECONDS = 86400L;

    /** The ladder, smallest first. Extend it here (not per chart) if a new size is ever needed. */
    private static final List<BucketInterval> NICE_INTERVALS = Collections.unmodifiableList(Arrays.asList(
            new BucketInterval(ChronoUnit.DAYS, 1, DAY_SECONDS, "Daily"),
            new BucketInterval(ChronoUnit.DAYS, 2, 2 * DAY_SECONDS, "Every 2 days"),
            new BucketInterval(ChronoUnit.WEEKS, 1, 7 * DAY_SECONDS, "Weekly"),
            new BucketInterval(ChronoUnit.WEEKS, 2, 14 * DAY_SECONDS, "Every 2 weeks"),
            new BucketInterval(ChronoUnit.MONTHS, 1, 30 * DAY_SECONDS, "Monthly"),
            new BucketInterval(ChronoUnit.MONTHS, 3, 90 * DAY_SECONDS, "Quarterly"),
            new BucketInterval(ChronoUnit.YEARS, 1, 365 * DAY_SECONDS, "Yearly")));

    /**
     * Picks the smallest ladder interval that keeps the range at or under {@code targetBars} bars.
     * Falls back to the largest rung when even that would exceed the target.
     */
    public static BucketInterval autoInterval(long startTs, long endTs, int targetBars) {
        long rangeSeconds = Math.max(1, endTs - startTs);
        double rawSeconds = (double) rangeSeconds / Math.max(1, targetBars);
        for (BucketInterval interval : NICE_INTERVALS) {
            if (interval.getApproxSeconds() >= rawSeconds) return interval;
        }
        return NICE_INTERVALS.get(NICE_INTERVALS.size() - 1);
    }

    /**
     * Start of every bar covering [startTs, endTs], aligned to the calendar in {@code zone}: days start at
     * local midnight, weeks on Monday, months on the 1st, quarters on Jan/Apr/Jul/Oct 1st, years on Jan 1st.
     * The first bar may therefore start slightly before startTs; it still only counts rows inside the range.
     * Multi-day bars (e.g. every 2 days) are counted from the day startTs falls on.
     */
    public static List<Long> bucketStarts(long startTs, long endTs, BucketInterval interval, ZoneId zone) {
        List<Long> starts = new ArrayList<>();
        if (endTs < startTs) return starts;
        LocalDate first = Instant.ofEpochSecond(startTs).atZone(zone).toLocalDate();
        switch (interval.getUnit()) {
            case WEEKS:
                first = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                break;
            case MONTHS:
                first = first.withDayOfMonth(1);
                if (interval.getAmount() == 3) first = first.withMonth(((first.getMonthValue() - 1) / 3) * 3 + 1);
                break;
            case YEARS:
                first = first.withDayOfYear(1);
                break;
            default:
                break;
        }
        for (LocalDate day = first; ; day = day.plus(interval.getAmount(), interval.getUnit())) {
            long start = day.atStartOfDay(zone).toEpochSecond();
            if (start > endTs) break;
            starts.add(start);
        }
        return starts;
    }

    /**
     * How many rows that pass {@code include} fall in each bar. Bar i covers [starts[i], starts[i+1]), the last
     * one [starts[last], endTs]. Rows outside [starts[0], endTs] are ignored. Works for any row type, so callers
     * pass their own timestamp getter instead of writing another counting loop.
     */
    public static <T> long[] countPerBucket(List<T> rows, ToLongFunction<T> timestampOf, Predicate<T> include,
                                            List<Long> starts, long endTs) {
        long[] counts = new long[starts.size()];
        if (rows == null || starts.isEmpty()) return counts;
        for (T row : rows) {
            if (row == null || !include.test(row)) continue;
            long ts = timestampOf.applyAsLong(row);
            if (ts < starts.get(0) || ts > endTs) continue;
            int index = Collections.binarySearch(starts, ts);
            counts[index >= 0 ? index : -index - 2]++;
        }
        return counts;
    }

    /** The viewer's IANA time zone (e.g. "Asia/Kolkata") sent by the browser, or the server's own when missing or invalid. */
    public static ZoneId zoneOrDefault(String timeZone) {
        if (timeZone == null || timeZone.trim().isEmpty()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(timeZone.trim());
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }
}
