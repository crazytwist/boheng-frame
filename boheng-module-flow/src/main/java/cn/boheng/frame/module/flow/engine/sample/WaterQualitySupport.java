package cn.boheng.frame.module.flow.engine.sample;

import cn.hutool.json.JSONUtil;

import java.util.List;
import java.util.Map;

/**
 * 水质检测示例的动作公共工具。上下文里的值来自 JSON 反序列化，
 * 这里统一做宽容的类型转换，让示例 Bean 的业务代码只关心水质语义。
 */
final class WaterQualitySupport {

    /**
     * 工具类，不创建实例
     */
    private WaterQualitySupport() {
    }

    /**
     * 当 Map 用。hutool 的 JSONObject 本身实现了 Map
     */
    static Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) m;
            return cast;
        }
        return Map.of();
    }

    /**
     * 当 List 用
     */
    static List<Object> list(Object value) {
        if (value instanceof List<?> l) {
            @SuppressWarnings("unchecked")
            List<Object> cast = (List<Object>) l;
            return cast;
        }
        return List.of();
    }

    /**
     * 当字符串用
     */
    static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 当整数用。hutool 反序列化数字可能是 Integer / Long / BigDecimal
     */
    static int intOf(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * 当小数用
     */
    static double doubleOf(Object value, double fallback) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * 设备原始报文（output_mapping 映出的 raw 字符串）解析成 Map
     */
    static Map<String, Object> parseRaw(Object raw) {
        String text = str(raw);
        if (text == null || text.isBlank() || !JSONUtil.isTypeJSONObject(text)) {
            return Map.of();
        }
        return map(JSONUtil.parseObj(text));
    }

}
