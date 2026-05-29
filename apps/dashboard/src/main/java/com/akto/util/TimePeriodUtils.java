package com.akto.util;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;
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
}
