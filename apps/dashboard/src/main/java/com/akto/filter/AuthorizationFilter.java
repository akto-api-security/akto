package com.akto.filter;

import com.akto.dto.User;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

// If user not present in session set by UserDetailsFilter then sends error
// which will make Vue.js to redirect to login page
public class AuthorizationFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) { }

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest= (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        HttpSession session = httpServletRequest.getSession();

        User user = (User) session.getAttribute("user");
        if (user == null &&  !httpServletRequest.getRequestURI().contains("api/googleConfig")) {
            httpServletResponse.sendError(401);
        }
        filterChain.doFilter(servletRequest, servletResponse);

    }

}
