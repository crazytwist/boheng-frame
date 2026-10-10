package cn.boheng.frame.module.device.service.simulate;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把仿真配置里的报文模板渲染成实际发出或返回的正文。
 */
public final class SimulateMessageRenderer {

    /**
     * 工具类，不创建实例
     */
    private SimulateMessageRenderer() {
    }

    /**
     * 用参数替换模板中的 ${参数名}。模板为空时原样返回。
     */
    public static String render(String template, Map<String, Object> params) {
        if (template == null) {
            return null;
        }
        String rendered = template;
        if (params == null || params.isEmpty()) {
            return rendered;
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            rendered = rendered.replace("${" + entry.getKey() + "}", value);
        }
        return rendered;
    }

    /**
     * 把调用参数 JSON 读成占位符字典。空白当成没有参数，不是对象时抛出非法参数。
     */
    public static Map<String, Object> paramsOf(String paramsJson) {
        if (StrUtil.isBlank(paramsJson)) {
            return Collections.emptyMap();
        }
        if (!JSONUtil.isTypeJSONObject(paramsJson)) {
            throw new IllegalArgumentException("参数不是合法的 JSON");
        }
        return new LinkedHashMap<>(JSONUtil.parseObj(paramsJson));
    }

    /**
     * 从请求正文里取出可用于响应模板的字段。不是 JSON 对象时返回空字典。
     */
    public static Map<String, Object> fieldsOf(String requestBody) {
        if (StrUtil.isBlank(requestBody) || !JSONUtil.isTypeJSONObject(requestBody)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(JSONUtil.parseObj(requestBody));
    }

    /**
     * 渲染报文，并在 JSON 对象上写入作业号，方便轮询方从正文里拿到它。
     */
    public static String renderReply(String template, String requestBody, String jobId) {
        Map<String, Object> params = fieldsOf(requestBody);
        if (StrUtil.isNotBlank(jobId)) {
            params.put("jobId", jobId);
        }
        String rendered = StrUtil.blankToDefault(render(template, params), "");
        if (StrUtil.isBlank(jobId) || !JSONUtil.isTypeJSONObject(rendered)) {
            return rendered;
        }
        JSONObject object = JSONUtil.parseObj(rendered);
        object.set("jobId", jobId);
        return object.toString();
    }

    /**
     * 按正文形状给出 Content-Type，让后续解析规则能区分 JSON、XML 和纯文本。
     */
    public static String mediaType(String body) {
        if (body == null) {
            return "application/json;charset=UTF-8";
        }
        String text = body.trim();
        if (text.startsWith("<")) {
            return "application/xml;charset=UTF-8";
        }
        if (text.startsWith("{") || text.startsWith("[")) {
            return "application/json;charset=UTF-8";
        }
        return "text/plain;charset=UTF-8";
    }

}
