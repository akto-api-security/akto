package com.akto.util;

public class DateUtils {

    public enum TrackingPeriod {
        DAILY, WEEKLY, MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY;

        public static DateUtils.TrackingPeriod getTrackingPeriod(int trackingPeriod) {
            return values()[trackingPeriod];
        }

    }
}
