package com.akto.listener;

import com.akto.dao.context.Context;

import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;

public class RequestEventListener implements ServletRequestListener {

    @Override
    public void requestInitialized(ServletRequestEvent sre) {
        Context.resetContextThreadLocals();
    }

    @Override
    public void requestDestroyed(ServletRequestEvent sre) {
        Context.resetContextThreadLocals();
    }

}
