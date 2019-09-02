package com.buzhidao.zzss.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Enumeration;

/**
 * created 2019/5/5 15:21
 */
@Slf4j
public class RequestLoggerFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        StringBuffer sb = new StringBuffer();
        sb.append(request.getRequestURL());

        String queryString = request.getQueryString();
        if (!StringUtils.isEmpty(queryString)){
            sb.append("?");
            sb.append(URLDecoder.decode(queryString));
        }
        sb.append("\n[Request Headers]\n");
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String header = names.nextElement();
            sb.append(header);
            sb.append(":");
            sb.append(request.getHeader(header));
            sb.append("\n");
        }
        log.info(sb.toString());

        chain.doFilter(request, response);
    }
}
