package com.codevam.vecindad.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class ClientInfo {
    private ClientInfo() {}

    public static String ip() {
        HttpServletRequest r = request();
        return r == null ? null : r.getRemoteAddr();
    }

    public static String userAgent() {
        HttpServletRequest r = request();
        if (r == null) return null;
        String ua = r.getHeader("User-Agent");
        return ua == null ? null : (ua.length() > 300 ? ua.substring(0, 300) : ua);
    }

    private static HttpServletRequest request() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes s ? s.getRequest() : null;
    }
}
