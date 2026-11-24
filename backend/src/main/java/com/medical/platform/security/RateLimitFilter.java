package com.medical.platform.security;

import com.medical.platform.identity.application.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 单机固定窗口限流（R6-B / 李梓语）。
 *
 * <p>覆盖三类高频入口，命中后返回 {@code 429 RATE_LIMITED}，响应体与
 * {@code docs/design/api-contract.md} 的错误结构一致：
 * <ul>
 *   <li>登录：按客户端 IP 计数，缓解撞库与暴力破解（威胁 T-02）；</li>
 *   <li>消息发送：按登录用户计数（无令牌时退化为 IP），缓解刷屏（T-14）；</li>
 *   <li>全局兜底：所有 {@code /api/v1/**} 按 IP 计数，避免列表/查询接口被无限刷。</li>
 * </ul>
 *
 * <p>实现取舍：只依赖进程内 {@link ConcurrentHashMap}，不引入 Redis/Lua，保证演示环境零额外依赖；
 * 代价是多实例部署时计数不共享、重启后清零，已在 {@code docs/security/r6-hardening.md} 列为残余风险。
 * 计数器按“自然分钟”窗口滚动，最多保留上一分钟的键，避免长期运行内存膨胀。
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern MESSAGE_PATH =
            Pattern.compile("^/api/v1/consultations/\\d+/messages$");
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String API_PREFIX = "/api/v1/";

    private final boolean enabled;
    private final int loginPerMinute;
    private final int messagePerMinute;
    private final int globalPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${app.rate-limit.enabled:true}") boolean enabled,
                           @Value("${app.rate-limit.login-per-minute:20}") int loginPerMinute,
                           @Value("${app.rate-limit.message-per-minute:60}") int messagePerMinute,
                           @Value("${app.rate-limit.global-per-minute:300}") int globalPerMinute) {
        this.enabled = enabled;
        this.loginPerMinute = loginPerMinute;
        this.messagePerMinute = messagePerMinute;
        this.globalPerMinute = globalPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !enabled
                || "OPTIONS".equalsIgnoreCase(request.getMethod())
                || uri == null
                || !uri.startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long minute = currentMinute();
        String clientIp = clientIp(request);

        List<Rule> rules = rulesFor(request, clientIp);
        for (Rule rule : rules) {
            if (!tryAcquire(rule.key(), rule.limit(), minute)) {
                reject(response, rule, minute);
                return;
            }
        }

        if (windows.size() > 5000) {
            evictExpired(minute);
        }
        filterChain.doFilter(request, response);
    }

    private List<Rule> rulesFor(HttpServletRequest request, String clientIp) {
        String uri = request.getRequestURI();
        Rule global = new Rule("global:" + clientIp, globalPerMinute);
        if ("POST".equalsIgnoreCase(request.getMethod()) && LOGIN_PATH.equals(uri)) {
            return List.of(global, new Rule("login:" + clientIp, loginPerMinute));
        }
        if ("POST".equalsIgnoreCase(request.getMethod()) && MESSAGE_PATH.matcher(uri).matches()) {
            return List.of(global, new Rule("message:" + subjectKey(request, clientIp), messagePerMinute));
        }
        return List.of(global);
    }

    /**
     * 消息限流优先按登录用户计数，避免同一出口 IP 下的多个患者互相影响；
     * 未解析出用户（理论上不会走到这里，消息接口要求认证）时退回 IP。
     */
    private String subjectKey(HttpServletRequest request, String clientIp) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return "user:" + user.id();
        }
        return "ip:" + clientIp;
    }

    private boolean tryAcquire(String key, int limit, long minute) {
        Window window = windows.computeIfAbsent(key, ignored -> new Window(minute));
        synchronized (window) {
            if (window.minute != minute) {
                window.minute = minute;
                window.count = 0;
            }
            if (window.count >= limit) {
                return false;
            }
            window.count += 1;
            return true;
        }
    }

    private void reject(HttpServletResponse response, Rule rule, long minute) throws IOException {
        long retryAfter = 60 - (currentSecond() % 60);
        response.setStatus(429);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setHeader("X-RateLimit-Limit", String.valueOf(rule.limit()));
        response.setHeader("X-RateLimit-Remaining", "0");
        String body = "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"请求过于频繁，请稍后重试\",\"code\":\"RATE_LIMITED\"}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }

    private void evictExpired(long minute) {
        windows.entrySet().removeIf(entry -> entry.getValue().minute < minute - 1);
    }

    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr;
    }

    private long currentMinute() {
        return System.currentTimeMillis() / 60_000L;
    }

    private long currentSecond() {
        return System.currentTimeMillis() / 1000L;
    }

    private record Rule(String key, int limit) {
    }

    private static final class Window {
        private long minute;
        private int count;

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
