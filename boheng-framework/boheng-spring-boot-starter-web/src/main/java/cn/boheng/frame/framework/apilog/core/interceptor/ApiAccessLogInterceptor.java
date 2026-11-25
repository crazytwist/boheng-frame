package cn.boheng.frame.framework.apilog.core.interceptor;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.boheng.frame.framework.common.util.servlet.ServletUtils;
import cn.boheng.frame.framework.common.util.spring.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * API 访问日志 Interceptor
 *
 * 目的：在非 prod 环境时，打印 request 和 response 两条日志到日志文件（控制台）中。
 *
 * <p>前端轮询的接口（如运行详情每 3 秒查一次）会把这类日志刷屏，冲掉真正的业务日志。
 * 用 {@code boheng.access-log.request-log.skip-urls} 按 URL 模式跳过，
 * 或用 {@code boheng.access-log.request-log.enabled=false} 整体关掉。
 * 跳过的只是控制台打印，落库的访问日志（{@code infra_api_access_log}）不受影响。</p>
 *
 * @author 伯珩
 */
@Slf4j
public class ApiAccessLogInterceptor implements HandlerInterceptor {

    public static final String ATTRIBUTE_HANDLER_METHOD = "HANDLER_METHOD";

    private static final String ATTRIBUTE_STOP_WATCH = "ApiAccessLogInterceptor.StopWatch";

    /**
     * 控制台请求日志总开关
     */
    private final boolean enabled;

    /**
     * 不打印的 URL 模式（Ant 风格）
     */
    private final List<String> skipUrls;

    /**
     * URL 模式匹配器
     */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ApiAccessLogInterceptor(boolean enabled, List<String> skipUrls) {
        this.enabled = enabled;
        this.skipUrls = skipUrls == null ? List.of() : List.copyOf(skipUrls);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 记录 HandlerMethod，提供给 ApiAccessLogFilter 使用。
        // 即使不打印日志也要记：落库的访问日志要靠它判断方法上有没有 @ApiAccessLog
        HandlerMethod handlerMethod = handler instanceof HandlerMethod ? (HandlerMethod) handler : null;
        if (handlerMethod != null) {
            request.setAttribute(ATTRIBUTE_HANDLER_METHOD, handlerMethod);
        }
        if (!shouldLog(request)) {
            return true;
        }

        // 打印 request 日志
        Map<String, String> queryString = ServletUtils.getParamMap(request);
        String requestBody = ServletUtils.getBody(request);
        if (CollUtil.isEmpty(queryString) && StrUtil.isEmpty(requestBody)) {
            log.info("[preHandle][开始请求 URL({}) 无参数]", request.getRequestURI());
        } else {
            log.info("[preHandle][开始请求 URL({}) 参数({})]", request.getRequestURI(),
                    StrUtil.blankToDefault(requestBody, queryString.toString()));
        }
        // 计时
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        request.setAttribute(ATTRIBUTE_STOP_WATCH, stopWatch);
        // 打印 Controller 路径
        printHandlerMethodPosition(handlerMethod);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 没有计时器说明这次没打印（prod、总开关关闭或被 skip-urls 跳过），直接返回。
        // 用属性判断而不是再算一遍 shouldLog：两个钩子必须严格配对，否则 stop 会空指针
        StopWatch stopWatch = (StopWatch) request.getAttribute(ATTRIBUTE_STOP_WATCH);
        if (stopWatch == null) {
            return;
        }
        stopWatch.stop();
        log.info("[afterCompletion][完成请求 URL({}) 耗时({} ms)]",
                request.getRequestURI(), stopWatch.getTotalTimeMillis());
    }

    /**
     * 这次请求要不要打印控制台日志
     */
    private boolean shouldLog(HttpServletRequest request) {
        if (!enabled || SpringUtils.isProd()) {
            return false;
        }
        String uri = request.getRequestURI();
        for (String pattern : skipUrls) {
            if (StrUtil.isNotBlank(pattern) && pathMatcher.match(pattern.trim(), uri)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 打印 Controller 方法路径
     */
    private void printHandlerMethodPosition(HandlerMethod handlerMethod) {
        if (handlerMethod == null) {
            return;
        }
        Method method = handlerMethod.getMethod();
        Class<?> clazz = method.getDeclaringClass();
        try {
            // 获取 method 的 lineNumber
            List<String> clazzContents = FileUtil.readUtf8Lines(
                    ResourceUtil.getResource(null, clazz).getPath().replace("/target/classes/", "/src/main/java/")
                            + clazz.getSimpleName() + ".java");
            Optional<Integer> lineNumber = IntStream.range(0, clazzContents.size())
                    .filter(i -> clazzContents.get(i).contains(" " + method.getName() + "(")) // 简单匹配，不考虑方法重名
                    .mapToObj(i -> i + 1) // 行号从 1 开始
                    .findFirst();
            if (!lineNumber.isPresent()) {
                return;
            }
            // 打印结果
            System.out.printf("\tController 方法路径：%s(%s.java:%d)\n", clazz.getName(), clazz.getSimpleName(), lineNumber.get());
        } catch (Exception ignore) {
            // 忽略异常。原因：仅仅打印，非重要逻辑
        }
    }

}
