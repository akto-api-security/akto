package com.akto.dao.context;

import com.akto.dao.AccountsDao;
import com.akto.dto.Account;
import com.akto.dto.Log;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

public class Context {
public static ThreadLocal<Integer> accountId = new ThreadLocal<Integer>();
public static ThreadLocal<Integer> userId = new ThreadLocal<Integer>();
public static ThreadLocal<CONTEXT_SOURCE> contextSource = new ThreadLocal<CONTEXT_SOURCE>();
public static ThreadLocal<Boolean> isRedactPayload = new ThreadLocal<>();
public static ThreadLocal<String> activityId = new ThreadLocal<String>();
public static ThreadLocal<Log.ActivityType> activityType = new ThreadLocal<Log.ActivityType>();

    public static void resetContextThreadLocals() {
        accountId.remove();
        userId.remove();
        contextSource.remove();
        isRedactPayload.remove();
        activityId.remove();
        activityType.remove();
    }

    /** Wraps {@code body} so it runs with accountId/userId/contextSource set on whatever thread
     *  actually executes it, clearing them again once it finishes — the one place this logic
     *  lives now, in place of an identical private copy that used to be pasted into
     *  ArgusPostureAction, SecurityPostureAction, InsightService, InsightDataLoader, and
     *  ArgusPostureService. Any {@code ExecutorService.submit(...)} fan-out across a worker-thread
     *  pool must wrap its task with this (or the accountId-only overload below) or the worker
     *  silently reads/writes the wrong account — these ThreadLocals are never inherited from the
     *  submitting thread. */
    public static <T> Callable<T> withContext(int accountId, Integer userId, CONTEXT_SOURCE contextSource, Callable<T> body) {
        return () -> {
            Context.accountId.set(accountId);
            Context.userId.set(userId);
            Context.contextSource.set(contextSource);
            try {
                return body.call();
            } finally {
                Context.accountId.remove();
                Context.userId.remove();
                Context.contextSource.remove();
            }
        };
    }

    /** accountId-only variant — for background jobs (e.g. PostureDrillNarrativeService's own
     *  narrative-generation tasks) that outlive the request thread and only need accountId
     *  re-set for their one account-scoped DAO call, not the full 3-field context. */
    public static <T> Callable<T> withContext(int accountId, Callable<T> body) {
        return () -> {
            Context.accountId.set(accountId);
            try {
                return body.call();
            } finally {
                Context.accountId.remove();
            }
        };
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
