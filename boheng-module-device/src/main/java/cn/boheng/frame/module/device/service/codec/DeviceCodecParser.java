package cn.boheng.frame.module.device.service.codec;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_CODEC_PARSE_FAILED;

/**
 * 把仪器原文按规则收成一个 JSON 对象。字段映射里的 wells 数组会在入库时拆成孔级读数。
 */
public final class DeviceCodecParser {

    private DeviceCodecParser() {
    }

    public static JSONObject parse(String parseType, String fieldMapping, String regexPattern, String raw) {
        if (StrUtil.isBlank(raw)) {
            throw exception(DEVICE_CODEC_PARSE_FAILED, "没有报文");
        }
        JSONObject mapping = parseMapping(fieldMapping);
        if ("REGEX".equalsIgnoreCase(parseType)) {
            return parseRegex(mapping, regexPattern, raw);
        }
        return parseJson(mapping, raw);
    }

    private static JSONObject parseMapping(String fieldMapping) {
        if (StrUtil.isBlank(fieldMapping)) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(fieldMapping);
        } catch (Exception ex) {
            throw exception(DEVICE_CODEC_PARSE_FAILED, "字段映射不是 JSON 对象");
        }
    }

    private static JSONObject parseJson(JSONObject mapping, String raw) {
        cn.hutool.json.JSON parsed;
        try {
            parsed = JSONUtil.parse(raw);
        } catch (Exception ex) {
            throw exception(DEVICE_CODEC_PARSE_FAILED, "报文不是 JSON");
        }
        if (mapping.isEmpty()) {
            if (parsed instanceof JSONObject object) {
                return object;
            }
            JSONObject wrapped = new JSONObject();
            wrapped.set("value", parsed);
            return wrapped;
        }
        JSONObject out = new JSONObject();
        mapping.forEach((key, value) -> {
            String path = String.valueOf(value).trim();
            if (path.startsWith("$.")) {
                path = path.substring(2);
            } else if ("$".equals(path)) {
                path = "";
            }
            out.set(key, StrUtil.isBlank(path) ? parsed : parsed.getByPath(path));
        });
        return out;
    }

    private static JSONObject parseRegex(JSONObject mapping, String regexPattern, String raw) {
        if (StrUtil.isBlank(regexPattern)) {
            throw exception(DEVICE_CODEC_PARSE_FAILED, "正则不能为空");
        }
        Matcher matcher;
        try {
            matcher = Pattern.compile(regexPattern).matcher(raw);
        } catch (Exception ex) {
            throw exception(DEVICE_CODEC_PARSE_FAILED, "正则不合法");
        }
        if (!matcher.find()) {
            throw exception(DEVICE_CODEC_PARSE_FAILED, "没有匹配到内容");
        }
        JSONObject out = new JSONObject();
        if (mapping.isEmpty()) {
            out.set("match", matcher.group());
            return out;
        }
        mapping.forEach((key, value) -> out.set(key, group(matcher, String.valueOf(value))));
        return out;
    }

    private static String group(Matcher matcher, String spec) {
        String token = spec == null ? "" : spec.trim();
        if (token.startsWith("group:")) {
            token = token.substring("group:".length());
        }
        try {
            if (token.matches("\\d+")) {
                return matcher.group(Integer.parseInt(token));
            }
            return matcher.group(token);
        } catch (Exception ex) {
            throw exception(DEVICE_CODEC_PARSE_FAILED, "捕获组 " + spec + " 不存在");
        }
    }

}
