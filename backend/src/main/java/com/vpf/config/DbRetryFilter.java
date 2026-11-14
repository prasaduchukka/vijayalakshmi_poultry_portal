package com.vpf.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.sql.SQLException;

/**
 * If a read-only (GET) API call hits a dead pooled database connection, retry it
 * once. Hikari evicts the broken connection and opens a fresh one, so the retry
 * normally succeeds and the user never sees the error.
 *
 * Only GET requests are retried: they never change data, so repeating them is
 * always safe. POST/PUT/DELETE are never retried automatically (avoids
 * duplicate payments, deliveries, etc.).
 *
 * Works together with GlobalExceptionHandler: the handler turns a connection
 * failure into a 503, and this filter notices the 503 before anything has been
 * written to the client and runs the request again.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class DbRetryFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DbRetryFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"GET".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        ContentCachingResponseWrapper first = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, first);
        } catch (ServletException | RuntimeException ex) {
            if (isConnectionProblem(ex) && !response.isCommitted()) {
                log.warn("DB connection problem on GET {} - retrying once", request.getRequestURI());
                chain.doFilter(request, response);
                return;
            }
            throw ex;
        }
        if (first.getStatus() == HttpServletResponse.SC_SERVICE_UNAVAILABLE && !response.isCommitted()) {
            log.warn("DB connection problem on GET {} - retrying once", request.getRequestURI());
            chain.doFilter(request, response);   // buffered 503 body is simply discarded
            return;
        }
        first.copyBodyToResponse();
    }

    private static boolean isConnectionProblem(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.JDBCConnectionException
                    || t instanceof org.springframework.dao.DataAccessResourceFailureException
                    || t instanceof java.sql.SQLTransientConnectionException
                    || t instanceof org.springframework.transaction.CannotCreateTransactionException) {
                return true;
            }
            if (t instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("08")) {
                return true;
            }
            if (t.getCause() == t) break;
        }
        return false;
    }
}
