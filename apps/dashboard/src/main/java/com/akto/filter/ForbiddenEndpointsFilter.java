package com.akto.filter;

import com.akto.listener.InitializerListener;
import com.akto.util.DashboardMode;
import io.github.bucket4j.Bucket;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ForbiddenEndpointsFilter implements Filter {

    private static final Set<String> forbiddenUrls = new HashSet<>(Arrays.asList(
            "api/fetchAdminSettings",
            "api/checkStackCreationProgress",
            "api/fetchDataFromWorksheet",
            "api/fetchLoadBalancers",
            "api/fetchLogs",
            "api/fetchLogsFromDb",
            "api/fetchTrafficMetrics",
            "api/fetchTrafficMetricsDesciptions",
            "api/fetchWorksheetsFromSheet",
            "api/getDriveNames",
            "api/getSpreadsheets",
            "api/googleConfig",
            "api/inventory/*/openapi",
            "api/saveLoadBalancers",
            "api/saveSubscription",
            "api/sendGoogleAuthCodeToServer",
            "api/takeUpdate",
            "api/toggleNewMergingEnabled",
            "api/toggleRedactFeature",
            "api/updateGlobalRateLimit",
            "api/updateMergeAsyncOutside",
            "api/updateSetupType",
            "signup-email",
            "signup-google"
    ));

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest= (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;

        String requestURI = httpServletRequest.getRequestURI();

        if (DashboardMode.isSaasDeployment() && forbiddenUrls.contains(requestURI)) {
            httpServletResponse.sendError(401);
            return ;
        }

        chain.doFilter(httpServletRequest, httpServletResponse);

    }

    @Override
    public void destroy() {

    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }
}