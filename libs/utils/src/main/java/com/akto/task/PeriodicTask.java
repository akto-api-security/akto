package com.akto.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

abstract public class PeriodicTask {

    int freqInHours;
    private static final Logger logger = LoggerFactory.getLogger(PeriodicTask.class);

    public void start(int freqInHours) {
        logger.info("starting task " + this.getClass().getName());
        ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        ses.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                execute();
            }
        }, 0, freqInHours, TimeUnit.HOURS);
        this.freqInHours = freqInHours;
    }

    public int getFreqInHours() {
        return freqInHours;
    }

    public abstract void execute();
}
