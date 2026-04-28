package ieti.jobswipe.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiMetricsInterceptor implements HandlerInterceptor {
    private static final String UNKNOWN = "unknown";

    private static final String SAMPLE_ATTRIBUTE = "jobswipe.api.timer.sample";
    private final MeterRegistry meterRegistry;

    public ApiMetricsInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(SAMPLE_ATTRIBUTE, Timer.start(meterRegistry));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        Object sampleObject = request.getAttribute(SAMPLE_ATTRIBUTE);
        if (!(sampleObject instanceof Timer.Sample sample)) {
            return;
        }

        String route = resolveRoute(request);
        String controller = UNKNOWN;
        String handlerMethod = UNKNOWN;

        if (handler instanceof HandlerMethod method) {
            controller = method.getBeanType().getSimpleName();
            handlerMethod = method.getMethod().getName();
        }

        String status = String.valueOf(response.getStatus());
        String exceptionType = ex != null ? ex.getClass().getSimpleName() : "none";

        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("route", route));
        tags.add(Tag.of("http_method", request.getMethod()));
        tags.add(Tag.of("status", status));
        tags.add(Tag.of("controller", controller));
        tags.add(Tag.of("handler", handlerMethod));
        tags.add(Tag.of("exception", exceptionType));

        sample.stop(Timer.builder("jobswipe.api.request.duration")
                .description("API request duration segmented by route/controller/status")
                .tags(tags)
                .register(meterRegistry));

        Counter.builder("jobswipe.api.request.count")
                .description("Total API requests segmented by route/controller/status")
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }

    private String resolveRoute(HttpServletRequest request) {
        Object bestPattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (bestPattern instanceof String pattern && !pattern.isBlank()) {
            return pattern;
        }

        String uri = request.getRequestURI();
        return (uri == null || uri.isBlank()) ? UNKNOWN : uri;
    }
}