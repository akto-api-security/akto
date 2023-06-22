package com.akto.filter;

import com.akto.dao.context.Context;

import javax.servlet.*;
import java.io.IOException;

public class GrowthToolsFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        Context.accountId.set(1_000_000);
        chain.doFilter(request, response);

    }

    @Override
    public void destroy() {
    }
}
