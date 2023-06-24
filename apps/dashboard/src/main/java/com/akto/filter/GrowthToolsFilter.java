package com.akto.filter;

import com.akto.dao.context.Context;
import com.akto.utils.DashboardMode;

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
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        String url = httpServletRequest.getRequestURL().toString();
        boolean isSaasDeployment = DashboardMode.isSaasDeployment();
        if (!isSaasDeployment && !url.startsWith("https://flash.staging.akto.io/")) {
            httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
            return;
        }
        Context.accountId.set(1_000_000);
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
