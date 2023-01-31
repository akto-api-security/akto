package com.akto.task;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

abstract public class PeriodicTask {

    int freqInHours;

    public void start(int freqInHours) {
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
