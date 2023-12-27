package com.akto.filter;

import com.akto.dao.context.Context;
import com.akto.listener.InitializerListener;
import com.akto.util.DashboardMode;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class GrowthToolsFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        boolean isSaasDeployment = InitializerListener.isSaas;
        if (!isSaasDeployment) {
            httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }
        String demoAccountIdStr = System.getenv("DEMO_ACCOUNT_ID");
        int demoAccountId = 1_000_000;
        if (demoAccountIdStr != null) {
            try {
                demoAccountId = Integer.parseInt(demoAccountIdStr);
            } catch (Exception e) {
            }
        }
        Context.accountId.set(demoAccountId);
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
