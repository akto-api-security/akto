package com.akto.dao.context;

import com.akto.dao.AccountsDao;
import com.akto.dto.Account;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class Context {
public static ThreadLocal<Integer> accountId = new ThreadLocal<Integer>();
public static ThreadLocal<Integer> userId = new ThreadLocal<Integer>();
public static ThreadLocal<CONTEXT_SOURCE> contextSource = new ThreadLocal<CONTEXT_SOURCE>();
public static ThreadLocal<Boolean> isRedactPayload = new ThreadLocal<>();

    public static void resetContextThreadLocals() {
        accountId.remove();
        userId.remove();
        contextSource.remove();
        isRedactPayload.remove();
    }

    public static int getId() {
        return (int) (System.currentTimeMillis()/1000l);
    }
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMddHH");


    public static void dummy() {
        
    }

    public static int today() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDateTime now = LocalDateTime.now();
        return Integer.parseInt(dtf.format(now));
    }

    public static int currentHour() {
        LocalDateTime now = LocalDateTime.now();
        return Integer.parseInt(dtf.format(now));
    }

    public static Account getAccount() {
        return AccountsDao.instance.findOne("_id", Context.accountId.get());
    }

    public static int convertEpochToDateInt(long epoch, String accountTz) {
        ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.of(accountTz));
        return Integer.parseInt(zonedDateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd")));
    }

    public static long convertDateIntToEpoch(int dateInt, String accountTz) {
        LocalDate localDate = LocalDate.parse(
                Integer.toString(dateInt),DateTimeFormatter.ofPattern("yyyyMMdd")
        );
        LocalTime localTime = LocalTime.MIDNIGHT;
        LocalDateTime localDateTime = LocalDateTime.of(localDate,localTime);
        ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime,ZoneId.of(accountTz));
        return zonedDateTime.toInstant().getEpochSecond();
    }

    public static ZonedDateTime convertEpochToZonedDateTime(long epoch, String accountTz) {
        return Instant.ofEpochSecond(epoch).atZone(ZoneId.of(accountTz));
    }

    public static ZonedDateTime setDateTimeToFirstOfMonth(ZonedDateTime zonedDateTime) {
        zonedDateTime = zonedDateTime.with(LocalTime.MIDNIGHT);
        zonedDateTime = zonedDateTime.withDayOfMonth(1);
        return zonedDateTime;
    }

    public static ZonedDateTime setTimeToMidnight(ZonedDateTime zonedDateTime) {
        return zonedDateTime.with(LocalTime.MIDNIGHT);
    }

    public static ZonedDateTime setMinutesAndSecondsToZero(ZonedDateTime zonedDateTime) {
        return zonedDateTime.withSecond(0).withMinute(0);
    }

    public static int now() {
        return (int) (System.currentTimeMillis()/1000l);
    }

    public static int nowInMillis() {
        return (int) (System.currentTimeMillis() % 100000000l);
    }

    public static Long epochInMillis() {
        return (Long) (System.currentTimeMillis());
    }


    public static long dateFromLotusNotation(BigDecimal serial_number, String sourceTz) {
        long numSecondsFromSheetEpoch = (long) (serial_number.doubleValue()*24*60*60);

        // Google sheets stores datetime in days from Dec 30 1899
        LocalDateTime start = LocalDateTime.of(1899, 12, 30, 0, 0, 0);
        LocalDateTime end = start.plusSeconds(numSecondsFromSheetEpoch);
        ZonedDateTime zonedDateTime = end.atZone(ZoneId.of(sourceTz));
        return zonedDateTime.toInstant().getEpochSecond();

    }
}
