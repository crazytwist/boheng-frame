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

    /**
     * 一次正则匹配最多跑多久，防止回溯过深的正则把线程卡死
     */
    private static final long REGEX_TIMEOUT_NANOS = 2_000_000_000L;

    /**
     * 正则匹配超时
     */
    private static final class RegexTimeoutException extends RuntimeException {

        /**
         * 不收集堆栈，超时是预期内的失败
         */
        RegexTimeoutException() {
            super(null, null, false, false);
        }
    }

    /**
     * 带截止时刻的原文。正则引擎每读一个字符都会经过 charAt，过了截止时刻就抛异常中断匹配
     */
    private record DeadlineCharSequence(CharSequence delegate, long deadlineNanos) implements CharSequence {

        /**
         * 原文长度
         */
        @Override
        public int length() {
            return delegate.length();
        }

        /**
         * 读一个字符，超时则中断
         */
        @Override
        public char charAt(int index) {
            if (System.nanoTime() > deadlineNanos) {
                throw new RegexTimeoutException();
            }
            return delegate.charAt(index);
        }

        /**
         * 截取片段，用于取捕获组
         */
        @Override
        public CharSequence subSequence(int start, int end) {
            return delegate.subSequence(start, end);
        }

        /**
         * 原文文本
         */
        @Override
        public String toString() {
            return delegate.toString();
        }
    }

    /**
     * 禁止实例化
     */
    private DeviceCodecParser() {
    }

    /**
     * 按 JSON 路径或正则，把原文收成一个对象
     */
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

    /**
     * 读字段映射。空映射表示按原文自身的结构
     */
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

    /**
     * 用字段映射里的路径从 JSON 取值
     */
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

    /**
     * 用正则捕获组按字段映射取值
     */
    private static JSONObject parseRegex(JSONObject mapping, String regexPattern, String raw) {
        if (StrUtil.isBlank(regexPattern)) {
            throw exception(DEVICE_CODEC_PARSE_FAILED, "正则不能为空");
        }
        Matcher matcher;
        try {
            matcher = Pattern.compile(regexPattern).matcher(new DeadlineCharSequence(raw, System.nanoTime() + REGEX_TIMEOUT_NANOS));
        } catch (Exception ex) {
            throw exception(DEVICE_CODEC_PARSE_FAILED, "正则表达式格式不正确");
        }
        boolean found;
        try {
            found = matcher.find();
        } catch (RegexTimeoutException ex) {
            throw exception(DEVICE_CODEC_PARSE_FAILED, "正则匹配超时，请简化正则表达式");
        }
        if (!found) {
            throw exception(DEVICE_CODEC_PARSE_FAILED, "未匹配到内容");
        }
        JSONObject out = new JSONObject();
        if (mapping.isEmpty()) {
            out.set("match", matcher.group());
            return out;
        }
        mapping.forEach((key, value) -> out.set(key, group(matcher, String.valueOf(value))));
        return out;
    }

    /**
     * 取出正则的一个捕获组。group:1 是序号，group:名称是命名组
     */
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
