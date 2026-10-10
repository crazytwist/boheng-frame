package cn.boheng.frame.module.device.service.simulate;

import cn.boheng.frame.framework.tenant.core.context.TenantContextHolder;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_HTTP_FAILED;

/**
 * 用真实 HTTP 访问本进程里的仿真设备，并带上当前租户。
 */
@Component
@Slf4j
public class SimulateHttpClient {

    /**
     * 写进命令记录的响应正文上限
     */
    private static final int BODY_LIMIT = 4000;

    /**
     * 单次轮询或同步调用的超时
     */
    private static final int DEFAULT_TIMEOUT_MS = 20_000;

    /**
     * 阶段响应头。仿真设备用它告诉调用方这次是受理、进行中还是已完成
     */
    public static final String PHASE_HEADER = "X-Simulate-Phase";

    /**
     * 作业号响应头。轮询地址靠它拼接
     */
    public static final String JOB_HEADER = "X-Simulate-Job-Id";

    /**
     * 仿真接口口令
     */
    @Resource
    private SimulateAccessGuard accessGuard;

    /**
     * POST 一次下发，附带命令号、回调地址等请求头
     */
    public SimulateHttpExchange post(String deviceCode, String url, String body, int timeoutMs, Map<String, String> headers) {
        return exchange(deviceCode, Method.POST, url, body, timeoutMs, headers);
    }

    /**
     * GET 一次轮询
     */
    public SimulateHttpExchange get(String deviceCode, String url) {
        return exchange(deviceCode, Method.GET, url, null, DEFAULT_TIMEOUT_MS, Collections.emptyMap());
    }

    /**
     * 发一次 HTTP，读回状态、阶段和作业号
     */
    private SimulateHttpExchange exchange(String deviceCode, Method method, String url, String body, int timeoutMs,
                                          Map<String, String> headers) {
        try {
            HttpRequest request = HttpRequest.of(url).method(method).timeout(Math.max(timeoutMs, 1_000));
            headers.forEach(request::header);
            request.header(SimulateAccessGuard.HEADER, accessGuard.secret());
            Long tenantId = TenantContextHolder.getTenantId();
            if (tenantId != null) {
                request.header("tenant-id", String.valueOf(tenantId));
            }
            if (body != null) {
                request.body(body).contentType(SimulateMessageRenderer.mediaType(body));
            }
            try (HttpResponse response = request.execute()) {
                String text = limit(response.body());
                return new SimulateHttpExchange(response.getStatus(), text, response.header("Content-Type"),
                        response.header(PHASE_HEADER), response.header(JOB_HEADER), method.name(), url, body);
            }
        } catch (Exception ex) {
            if (ex instanceof cn.boheng.frame.framework.common.exception.ServiceException serviceException) {
                throw serviceException;
            }
            log.warn("[exchange][设备({}) 仿真 HTTP {} 失败]", deviceCode, url, ex);
            throw exception(DEVICE_HTTP_FAILED, deviceCode, ex.getMessage());
        }
    }

    /**
     * 截断过长正文，避免命令记录被一次大报文撑满
     */
    private static String limit(String text) {
        if (text != null && text.length() > BODY_LIMIT) {
            return text.substring(0, BODY_LIMIT);
        }
        return text;
    }

    /**
     * 一次打到仿真设备的 HTTP 结果。
     *
     * @param status HTTP 状态码
     * @param body 响应正文
     * @param contentType 响应类型
     * @param phase 仿真阶段
     * @param jobId 轮询作业号
     * @param httpMethod 实际方法
     * @param requestUrl 实际地址
     * @param requestBody 实际请求正文
     */
    public record SimulateHttpExchange(int status, String body, String contentType, String phase, String jobId,
                                       String httpMethod, String requestUrl, String requestBody) {

        /**
         * 是否已经拿到最终报文
         */
        public boolean done() {
            return StrUtil.equalsIgnoreCase(phase, SimulateDeviceReply.DONE);
        }

    }

}
