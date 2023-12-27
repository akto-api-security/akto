package com.akto.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class DateUtils {

    public static int getHour(int epoch){
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());

        return dateTime.getHour();
    }

    public static int getDateYYYYMMDD(int epoch){
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());

        return dateTime.getYear() * 10000 + dateTime.getMonthValue() * 100 + dateTime.getDayOfMonth();
    }
}
