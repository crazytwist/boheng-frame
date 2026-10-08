package cn.boheng.frame.module.device.service.http;

import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.Method;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.Map;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ENDPOINT_MISSING;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_HTTP_FAILED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_LOGIN_FAILED;

/**
 * 访问设备 HTTP：配置了登录路径时先取 token，失效后再登录一次并重试。
 */
@Component
@Slf4j
public class DeviceHttpGateway {

    private static final int TIMEOUT_MS = 20_000;
    private static final int DEFAULT_TTL_SEC = 1800;
    private static final int BODY_LIMIT = 4000;

    @Resource
    private DeviceLoginTokenCache tokenCache;

    public DeviceInvokeRespVO invoke(DeviceInfoDO device, DeviceActionDO action, String paramsJson) {
        String url = joinUrl(device.getEndpointUrl(), action.getRequestPath());
        if (url == null) {
            throw exception(DEVICE_ENDPOINT_MISSING, device.getDeviceCode());
        }
        Map<String, Object> params = parseParams(device, paramsJson);
        String method = StrUtil.blankToDefault(action.getHttpMethod(), "POST").toUpperCase();
        String format = StrUtil.blankToDefault(action.getBodyFormat(), "JSON").toUpperCase();
        String body = requestBody(device, method, format, action.getRequestTemplate(), params);

        boolean login = StrUtil.isNotBlank(device.getLoginPath());
        String token = login ? token(device, false) : null;
        HttpResult result = exchange(device, method, url, body, params, token, format);
        boolean retried = false;
        if (login && result.status == 401) {
            log.info("[invoke][设备({}) 动作({}) token 失效，重新登录]", device.getDeviceCode(), action.getActionCode());
            token = token(device, true);
            result = exchange(device, method, url, body, params, token, format);
            retried = true;
        }
        DeviceInvokeRespVO resp = new DeviceInvokeRespVO();
        resp.setStatus(result.status);
        resp.setBody(result.body);
        resp.setContentType(result.contentType);
        resp.setRetriedLogin(retried);
        resp.setHttpMethod(method);
        resp.setRequestUrl(url);
        resp.setRequestBody(body);
        return resp;
    }

    public void evict(Long deviceId) {
        tokenCache.evict(deviceId);
    }

    private String token(DeviceInfoDO device, boolean force) {
        if (!force) {
            String cached = tokenCache.getValid(device.getId());
            if (cached != null) {
                return cached;
            }
        } else {
            tokenCache.evict(device.getId());
        }
        synchronized (deviceLock(device.getId())) {
            if (!force) {
                String cached = tokenCache.getValid(device.getId());
                if (cached != null) {
                    return cached;
                }
            }
            String fresh = login(device);
            int ttl = device.getTokenTtlSec() == null || device.getTokenTtlSec() < 1
                    ? DEFAULT_TTL_SEC : device.getTokenTtlSec();
            tokenCache.put(device.getId(), fresh, ttl);
            return fresh;
        }
    }

    private final java.util.concurrent.ConcurrentHashMap<Long, Object> locks = new java.util.concurrent.ConcurrentHashMap<>();

    private Object deviceLock(Long deviceId) {
        return locks.computeIfAbsent(deviceId, id -> new Object());
    }

    private String login(DeviceInfoDO device) {
        String url = joinUrl(device.getEndpointUrl(), device.getLoginPath());
        if (url == null) {
            throw exception(DEVICE_LOGIN_FAILED, device.getDeviceCode(), "没有登录地址");
        }
        String method = StrUtil.blankToDefault(device.getLoginMethod(), "POST").toUpperCase();
        JSONObject payload = new JSONObject();
        payload.set(StrUtil.blankToDefault(device.getLoginUsernameKey(), "username"),
                StrUtil.blankToDefault(device.getLoginUsername(), ""));
        payload.set(StrUtil.blankToDefault(device.getLoginPasswordKey(), "password"),
                StrUtil.blankToDefault(device.getLoginPassword(), ""));
        HttpResult result = exchange(device, method, url, payload.toString(), Collections.emptyMap(), null, "JSON");
        if (result.status < 200 || result.status >= 300) {
            throw exception(DEVICE_LOGIN_FAILED, device.getDeviceCode(), "HTTP " + result.status);
        }
        String token = readToken(result.body, StrUtil.blankToDefault(device.getTokenPath(), "token"));
        if (StrUtil.isBlank(token)) {
            throw exception(DEVICE_LOGIN_FAILED, device.getDeviceCode(), "响应里没有 token");
        }
        return token;
    }

    private HttpResult exchange(DeviceInfoDO device, String method, String url, String body,
                                Map<String, Object> query, String token, String format) {
        try {
            HttpRequest request = HttpRequest.of(url).method(Method.valueOf(method)).timeout(TIMEOUT_MS);
            if (StrUtil.isNotBlank(token)) {
                request.header(StrUtil.blankToDefault(device.getTokenHeader(), "Authorization"), headerValue(device, token));
            }
            boolean queryOnly = "GET".equals(method) || "DELETE".equals(method);
            if (queryOnly) {
                writeForm(request, query);
            } else if ("FORM".equals(format) && body == null) {
                writeForm(request, query);
            } else if (body != null) {
                request.body(body).contentType(mediaType(format));
            }
            try (HttpResponse response = request.execute()) {
                String text = response.body();
                if (text != null && text.length() > BODY_LIMIT) {
                    text = text.substring(0, BODY_LIMIT);
                }
                return new HttpResult(response.getStatus(), text, response.header("Content-Type"));
            }
        } catch (IllegalArgumentException ex) {
            throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(), "不支持的方法 " + method);
        } catch (Exception ex) {
            if (ex instanceof cn.boheng.frame.framework.common.exception.ServiceException serviceException) {
                throw serviceException;
            }
            log.warn("[exchange][设备({}) 请求 {} 失败]", device.getDeviceCode(), url, ex);
            throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(), ex.getMessage());
        }
    }

    static String joinUrl(String base, String path) {
        if (StrUtil.isBlank(base) || StrUtil.isBlank(path)) {
            return null;
        }
        String root = base.trim();
        if (!root.startsWith("http://") && !root.startsWith("https://")) {
            return null;
        }
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        String suffix = path.trim();
        if (!suffix.startsWith("/")) {
            suffix = "/" + suffix;
        }
        return root + suffix;
    }

    static String readToken(String body, String path) {
        if (StrUtil.isBlank(body) || !JSONUtil.isTypeJSON(body)) {
            return null;
        }
        Object value = JSONUtil.parseObj(body).getByPath(path);
        return value == null ? null : String.valueOf(value);
    }

    private static String headerValue(DeviceInfoDO device, String token) {
        String prefix = device.getTokenPrefix();
        if (StrUtil.isBlank(prefix)) {
            return token;
        }
        String spaced = prefix.endsWith(" ") ? prefix : prefix + " ";
        if (token.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return token;
        }
        return spaced + token;
    }

    private static Map<String, Object> parseParams(DeviceInfoDO device, String paramsJson) {
        if (StrUtil.isBlank(paramsJson)) {
            return Collections.emptyMap();
        }
        try {
            JSONObject obj = JSONUtil.parseObj(paramsJson);
            return obj;
        } catch (Exception ex) {
            throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(), "参数不是合法的 JSON");
        }
    }

    private static String requestBody(DeviceInfoDO device, String method, String format, String template,
                                      Map<String, Object> params) {
        if ("GET".equals(method) || "DELETE".equals(method)) {
            return null;
        }
        String rendered = render(template, params);
        if ("FORM".equals(format)) {
            return StrUtil.isNotBlank(template) ? rendered : null;
        }
        if ("TEXT".equals(format)) {
            return StrUtil.isNotBlank(template) ? rendered : plainLines(params);
        }
        if ("XML".equals(format)) {
            if (StrUtil.isBlank(template)) {
                throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(), "XML 需要填写请求模板");
            }
            return rendered;
        }
        if (StrUtil.isNotBlank(template)) {
            return rendered;
        }
        return params.isEmpty() ? null : JSONUtil.toJsonStr(params);
    }

    private static String render(String template, Map<String, Object> params) {
        if (StrUtil.isBlank(template)) {
            return template;
        }
        String rendered = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            rendered = rendered.replace("${" + entry.getKey() + "}", value);
        }
        return rendered;
    }

    private static String plainLines(Map<String, Object> params) {
        if (params.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        params.forEach((key, value) -> {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(key).append('=').append(value == null ? "" : value);
        });
        return text.toString();
    }

    private static void writeForm(HttpRequest request, Map<String, Object> query) {
        if (query == null) {
            return;
        }
        query.forEach((key, value) -> {
            if (value != null) {
                request.form(key, value);
            }
        });
    }

    private static String mediaType(String format) {
        return switch (format) {
            case "FORM" -> "application/x-www-form-urlencoded;charset=UTF-8";
            case "TEXT" -> "text/plain;charset=UTF-8";
            case "XML" -> "application/xml;charset=UTF-8";
            default -> "application/json;charset=UTF-8";
        };
    }

    private record HttpResult(int status, String body, String contentType) {
    }

}
