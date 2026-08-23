package com.apextick.booking.security.ratelimit;

import com.apextick.booking.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Throttles the write-heavy hold/order/pay endpoints per authenticated subject (or IP). */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisRateLimiter limiter;
    private final AppProperties props;

    public RateLimitInterceptor(RedisRateLimiter limiter, AppProperties props) {
        this.limiter = limiter;
        this.props = props;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!props.rateLimit().enabled() || !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        String bucket;
        AppProperties.Bucket cfg;
        if (uri.matches("/api/events/[^/]+/holds")) {
            bucket = "hold";
            cfg = props.rateLimit().hold();
        } else if (uri.equals("/api/orders") || uri.matches("/api/orders/[^/]+/pay")) {
            bucket = "order";
            cfg = props.rateLimit().order();
        } else {
            return true;
        }

        RedisRateLimiter.Decision decision = limiter.tryAcquire(bucket, subject(request), cfg.limit(), cfg.window());
        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            throw new RateLimitExceededException(decision.retryAfterSeconds());
        }
        return true;
    }

    private String subject(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return jwt.getName();
        }
        return request.getRemoteAddr();
    }
}
