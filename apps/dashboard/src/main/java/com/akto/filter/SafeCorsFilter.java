package com.akto.filter;

import com.akto.util.DashboardMode;
import org.eclipse.jetty.servlets.CrossOriginFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SafeCorsFilter extends CrossOriginFilter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String origin = httpRequest.getHeader("Origin");

            if (origin != null && !isValidAktoOrigin(origin)) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        } catch (Exception e) {
            System.out.println("error chacking cors filer" + e.getMessage());
        }

        super.doFilter(request, response, chain);
    }

    private boolean isValidAktoOrigin(String origin) {
        String host = origin.replaceFirst("^https?://", "").split(":")[0];
        return host.equals("akto.io") || host.endsWith(".akto.io");
    }
}
