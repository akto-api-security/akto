package com.akto.log;

import com.akto.dao.LogsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Log;
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



}
