package com.akto.interceptor;

import com.akto.action.test_editor.SaveTestEditorAction;
import com.akto.gpt.handlers.gpt_prompts.TestExecutorModifier;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.test_editor.Utils;
import com.akto.util.DashboardMode;
import com.opensymphony.xwork2.ActionInvocation;

public class ContextBasedUsageInterceptor extends UsageInterceptor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ContextBasedUsageInterceptor.class, LogDb.DASHBOARD);

    @Override
    public String intercept(ActionInvocation invocation) throws Exception {

        try {

            if (!DashboardMode.isMetered()) {
                return invocation.invoke();
            }

            Object actionObject = invocation.getInvocationContext().getActionInvocation().getAction();

            if (actionObject instanceof SaveTestEditorAction) {
                SaveTestEditorAction action = (SaveTestEditorAction) actionObject;
                if (action.getContent() != null && action.getContent().length() > 0
                        && action.getContent().contains(Utils._MAGIC)) {
                    invocation.getInvocationContext().put(_FEATURE_LABEL, TestExecutorModifier._AKTO_GPT_AI);
                    return invocation.invoke();
                }
            }

        } catch (Exception e) {
            String api = invocation.getProxy().getActionName();
            String error = "Error in ContextBasedUsageInterceptor for api: " + api + " ERROR: " + e.getMessage();
            loggerMaker.errorAndAddToDb(e, error);
        }

        return invocation.invoke();

    }

}
