package ro.unibuc.prodeng.metrics;

import java.io.IOException;
import java.time.Duration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

public class RequestMetricsFilter extends OncePerRequestFilter {

    @Autowired(required = false)
    private AppMetricsService appMetricsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startTime = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (appMetricsService != null) {
                Object bestPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String endpoint = bestPattern != null ? bestPattern.toString() : request.getRequestURI();
                appMetricsService.recordRequestDuration(
                        endpoint,
                        request.getMethod(),
                        response.getStatus(),
                        Duration.ofNanos(System.nanoTime() - startTime));
            }
        }
    }
}