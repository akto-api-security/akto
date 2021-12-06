package com.akto.calendar;

import com.akto.util.DateUtils.TrackingPeriod;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class DateUtils {


    public static int date(LocalDate of) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd");
        return Integer.parseInt(dtf.format(of));
    }

    public static LocalDate getStartLocalDate(TrackingPeriod period, int date) {
        switch (period) {
            case DAILY: return LocalDate.of(date/10000, (date/100)%100, date%100);
            case WEEKLY: return LocalDate.of(date/10000, (date/100)%100, date%100).with(DayOfWeek.MONDAY);
            case MONTHLY: return LocalDate.of(date/10000, (date/100)%100, 1);
            case QUARTERLY: return LocalDate.of(date/10000, ((date/100)%100-1)/3 * 3+1, 1);
            case HALF_YEARLY: LocalDate.of(date/10000, (date/100)%100 <= 6 ? 1 : 7, 1);
            case YEARLY: LocalDate.of(date/10000, 1, 1);
            default:
                throw new IllegalArgumentException("Invalid Tracking period: " + period);
        }
    }

    public static int getStartDate(TrackingPeriod period, int date) {
        return date(getStartLocalDate(period, date));
    }

    public static int getEndDate(TrackingPeriod period, int date) {

        LocalDate startDate = getStartLocalDate(period, date);

        switch (period) {
            case DAILY: return date(startDate);
            case WEEKLY: return date(startDate.plusDays(6));
            case MONTHLY: return date(startDate.plusMonths(1).minusDays(1));
            case QUARTERLY: return date(startDate.plusMonths(3).minusDays(1));
            case HALF_YEARLY: return date(startDate.plusMonths(6).minusDays(1));
            case YEARLY: return date(LocalDate.of(date/10000, 12, 31));
            default:
                throw new IllegalArgumentException("Invalid Tracking period: " + period);
        }
    }

    public static long getNumberOfDaysLeftInPeriod(int startDate, int endDate) {
        LocalDate localStartDate = LocalDate.parse(Integer.toString(startDate),DateTimeFormatter.ofPattern("yyyyMMdd"));
        LocalDate localEndDate = LocalDate.parse(Integer.toString(endDate),DateTimeFormatter.ofPattern("yyyyMMdd"));

        return localStartDate.until(localEndDate, ChronoUnit.DAYS) + 1;
    }
}
