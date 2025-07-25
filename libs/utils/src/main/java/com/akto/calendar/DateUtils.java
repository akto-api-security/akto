package com.akto.calendar;

import com.akto.dao.context.Context;
import com.akto.util.DateUtils.TrackingPeriod;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class DateUtils {


    public static int date(LocalDate of) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd");
        return Integer.parseInt(dtf.format(of));
    }

    public static String prettifyDelta(int epochInSeconds) {
        int diff = Context.now() - epochInSeconds;
        if (diff < 120) {
            return "1 minute ago";
        } else if (diff < 3600) {
            return diff/60 + " minutes ago";
        } else if (diff < 7200) {
            return "1 hour ago";
        } else if (diff < 86400) {
            return diff/3600 + " hours ago";
        } else if (diff < 172_800) {
            return "1 day ago";
        }

        return diff/86400 + " days ago";
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

    public static String convertToUtcLocaleDate(long epochTime) {
        Date date = new Date(epochTime);
        SimpleDateFormat formatter = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(date);
    }
}
