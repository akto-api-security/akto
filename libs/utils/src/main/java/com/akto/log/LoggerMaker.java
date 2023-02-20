package com.akto.log;

import com.akto.dao.LogsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Log;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

import java.util.ArrayList;
import java.util.List;

import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggerMaker  {

    public final Logger logger;
    private final Class<?> aClass;

    public LoggerMaker(Class<?> c) {
        aClass = c;
        logger = LoggerFactory.getLogger(c);
    }

    public void errorAndAddToDb(String err) {
        logger.error(err);
        insert(err, "error");
    }

    public void infoAndAddToDb(String info) {
        logger.info(info);
        insert(info, "info");
    }

    private void insert(String info, String key) {
        String text = aClass + " : " + info;
        Log log = new Log(text, key, Context.now());
        LogsDao.instance.insertOne(log);
    }

    public List<Log> fetchLogRecords(int logFetchStartTime, int logFetchEndTime) {

        List<Log> logs = new ArrayList<>();

        Bson filters = Filters.and(
            Filters.gte(Log.TIMESTAMP, logFetchStartTime),
            Filters.lte(Log.TIMESTAMP, logFetchEndTime)
        );
        logs = LogsDao.instance.findAll(filters, Projections.include("log", Log.TIMESTAMP));
        return logs;
    }
}
