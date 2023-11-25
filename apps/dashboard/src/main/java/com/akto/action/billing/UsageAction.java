package com.akto.action.billing;

import com.akto.action.UserAction;
import com.akto.listener.InitializerListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UsageAction extends UserAction {

    public static final ExecutorService ex = Executors.newFixedThreadPool(1);

    public String syncUsage() {

        ex.submit(new Runnable() {
            @Override
            public void run() {
                InitializerListener.calcUsage();
                InitializerListener.syncWithAkto();
            }
        });

        return SUCCESS.toUpperCase();
    }
    @Override
    public String execute() {
        throw new UnsupportedOperationException();
    }
}