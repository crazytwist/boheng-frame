package cn.boheng.frame.module.device.service.http;

import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackService;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackTicket;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ENDPOINT_MISSING;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_HTTP_FAILED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_LOGIN_FAILED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_TEMPLATE_INVALID;

/**
 * 访问设备 HTTP：配置了登录路径时先取 token，失效后再登录一次并重试。
 */
@Component
@Slf4j
public class DeviceHttpGateway {

    /** 单次 HTTP 超时，毫秒 */
    private static final int TIMEOUT_MS = 20_000;
    /** 台账没填有效秒数时，令牌默认缓存 1800 秒 */
    private static final int DEFAULT_TTL_SEC = 1800;
    /** 写进命令记录的响应正文上限 */
    private static final int BODY_LIMIT = 4000;
    /** 渲染后仍未替换的模板占位符。 */
    private static final Pattern UNRESOLVED_PLACEHOLDER = Pattern.compile("\\$\\{[^}]+}");
    /**
     * 登录令牌缓存
     */

    @Resource
    private DeviceLoginTokenCache tokenCache;

    /**
     * 向设备发一次 HTTP。配了登录路径时先带上令牌，401 时重新登录再发一次。
     * 等回调的命令会多带命令号、回调地址和回调令牌，模板里也能用 ${commandNo}、${callbackUrl}、${callbackToken}。
     */
    public DeviceInvokeRespVO invoke(DeviceInfoDO device, DeviceActionDO action, String paramsJson,
                                     String commandNo, DeviceCallbackTicket ticket) {
        String url = joinUrl(device.getEndpointUrl(), action.getRequestPath());
        if (url == null) {
            throw exception(DEVICE_ENDPOINT_MISSING, device.getDeviceCode());
        }
        Map<String, Object> params = parseParams(device, paramsJson);
        Map<String, Object> vars = new LinkedHashMap<>(params);
        Map<String, String> headers = new LinkedHashMap<>();
        if (StrUtil.isNotBlank(commandNo)) {
            vars.putIfAbsent("commandNo", commandNo);
            headers.put(DeviceCallbackService.COMMAND_HEADER, commandNo);
        }
        if (ticket != null) {
            vars.putIfAbsent("callbackUrl", ticket.url());
            vars.putIfAbsent("callbackToken", ticket.token());
            headers.put(DeviceCallbackService.CALLBACK_URL_HEADER, ticket.url());
            headers.put(DeviceCallbackService.TOKEN_HEADER, ticket.token());
        }
        String method = StrUtil.blankToDefault(action.getHttpMethod(), "POST").toUpperCase();
        String format = StrUtil.blankToDefault(action.getBodyFormat(), "JSON").toUpperCase();
        String body = requestBody(device, method, format, action.getRequestTemplate(), params, vars);

        boolean login = StrUtil.isNotBlank(device.getLoginPath());
        String token = login ? token(device, false) : null;
        HttpResult result = exchange(device, method, url, body, params, token, format, headers);
        boolean retried = false;
        if (login && result.status == 401) {
            log.info("[invoke][设备({}) 动作({}) token 失效，重新登录]", device.getDeviceCode(), action.getActionCode());
            token = token(device, true);
            result = exchange(device, method, url, body, params, token, format, headers);
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

    /**
     * 丢掉这台设备缓存的登录令牌
     */
    public void evict(Long deviceId) {
        tokenCache.evict(deviceId);
    }

    /**
     * 取缓存令牌；强制刷新或过期时重新登录
     */
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

    /** 按设备编号存放的登录锁 */
    private final java.util.concurrent.ConcurrentHashMap<Long, Object> locks = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 同一台设备的登录互斥锁
     */
    private Object deviceLock(Long deviceId) {
        return locks.computeIfAbsent(deviceId, id -> new Object());
    }

    /**
     * 按台账上的登录配置取 token
     */
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
        HttpResult result = exchange(device, method, url, payload.toString(), Collections.emptyMap(), null, "JSON",
                Collections.emptyMap());
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
                                Map<String, Object> query, String token, String format, Map<String, String> headers) {
        try {
            HttpRequest request = HttpRequest.of(url).method(Method.valueOf(method)).timeout(TIMEOUT_MS);
            headers.forEach(request::header);
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

    /**
     * 把台账上的主机和动作上的相对路径拼成完整地址
     */
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

    /**
     * 按 JSON 路径从登录响应里取出 token
     */
    static String readToken(String body, String path) {
        if (StrUtil.isBlank(body) || !JSONUtil.isTypeJSON(body)) {
            return null;
        }
        Object value = JSONUtil.parseObj(body).getByPath(path);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 给 token 加上前缀，已经带前缀的不再重复加
     */
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
                                      Map<String, Object> params, Map<String, Object> vars) {
        if ("GET".equals(method) || "DELETE".equals(method)) {
            return null;
        }
        String rendered = render(template, vars);
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

    /**
     * 用参数替换模板里的 ${参数名}
     */
    private static String render(String template, Map<String, Object> params) {
        if (StrUtil.isBlank(template)) {
            return template;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        params.forEach((key, value) -> flatten(values, key, value));
        String rendered = template;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String value = renderValue(entry.getValue());
            rendered = rendered.replace("${" + entry.getKey() + "}", value);
            rendered = rendered.replace("${$." + entry.getKey() + "}", value);
        }
        if (UNRESOLVED_PLACEHOLDER.matcher(rendered).find()) {
            throw exception(DEVICE_ACTION_TEMPLATE_INVALID, "存在未解析占位符");
        }
        return rendered;
    }

    /** 展开对象和数组，兼容 ${sample.id}、${items[0].code} 以及旧的一级参数。 */
    private static void flatten(Map<String, Object> values, String path, Object value) {
        values.put(path, value);
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, child) -> flatten(values, path + "." + key, child));
        } else if (value instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object child : iterable) {
                flatten(values, path + "[" + index++ + "]", child);
            }
        }
    }

    private static String renderValue(Object value) {
        if (value == null) return "";
        if (value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return JSONUtil.toJsonStr(value);
        }
        return String.valueOf(value);
    }

    /**
     * 没有模板时，纯文本按参数名=值逐行拼
     */
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

    /**
     * 把参数写成表单或查询参数
     */
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

    /**
     * 报文格式对应的 Content-Type
     */
    private static String mediaType(String format) {
        return switch (format) {
            case "FORM" -> "application/x-www-form-urlencoded;charset=UTF-8";
            case "TEXT" -> "text/plain;charset=UTF-8";
            case "XML" -> "application/xml;charset=UTF-8";
            default -> "application/json;charset=UTF-8";
        };
    }

    /**
     * 一次 HTTP 交换的结果。
     *
     * @param status HTTP 状态码
     * @param body 响应正文
     * @param contentType 响应 Content-Type
     */
    private record HttpResult(int status, String body, String contentType) {
    }

}
