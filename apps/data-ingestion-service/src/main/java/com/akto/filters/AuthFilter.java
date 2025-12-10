package com.akto.filters;

import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class AuthFilter implements Filter {

    private static final LoggerMaker logger = new LoggerMaker(AuthFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    private static Jws<Claims> authenticate(String jwsString)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {

         PublicKey publicKey = getPublicKey();
         return Jwts.parserBuilder()
                 .setSigningKey(publicKey)
                 .build()
                 .parseClaimsJws(jwsString);
    }

    private static PublicKey getPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
       String rsaPublicKey = System.getenv("RSA_PUBLIC_KEY");
        if(rsaPublicKey == null || rsaPublicKey.isEmpty()) {
            throw new IllegalArgumentException("RSA_PUBLIC_KEY environment variable is not set");
        }

        rsaPublicKey = rsaPublicKey.replace("-----BEGIN PUBLIC KEY-----","");
        rsaPublicKey = rsaPublicKey.replace("-----END PUBLIC KEY-----","");
        rsaPublicKey = rsaPublicKey.replace("\n","");
        byte [] decoded = Base64.getDecoder().decode(rsaPublicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");

        try {
            return kf.generatePublic(keySpec);
        } catch (Exception e) {
            System.out.println(e);
            throw e;
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException {
        String isAuthenticated = System.getenv().getOrDefault("AKTO_DI_AUTHENTICATE", "");
        if (isAuthenticated.isEmpty() || isAuthenticated.equalsIgnoreCase("false")) {
            logger.warn("Skipping authentication as AKTO_DI_AUTHENTICATE=true is not set");
            chain.doFilter(servletRequest, servletResponse);
            return;
        }
        HttpServletRequest httpServletRequest= (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String accessTokenFromRequest = httpServletRequest.getHeader("authorization");

        try {
            Jws<Claims> claims = authenticate(accessTokenFromRequest);
            Context.accountId.set((int) claims.getBody().get("accountId"));
        } catch (Exception e) {
            logger.error("Authentication failed: {}", e.getMessage());
            httpServletResponse.sendError(401);
            return;
        }
        chain.doFilter(servletRequest, servletResponse);

    }

    @Override
    public void destroy() {

    }
}