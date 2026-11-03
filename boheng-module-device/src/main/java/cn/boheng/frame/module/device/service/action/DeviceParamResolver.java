package cn.boheng.frame.module.device.service.action;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_PARAM_INVALID;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_PARAM_SCHEMA_INVALID;

/**
 * 设备动作参数解析器。
 *
 * <p>参数定义是动作输入的唯一契约。本解析器负责展开默认值并校验必填、类型和约束，
 * 参数集、流程映射和人工调试最终都必须经过同一入口。</p>
 */
@Component
public class DeviceParamResolver {

    /** 输入契约允许声明的参数类型。 */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "STRING", "INTEGER", "DECIMAL", "BOOLEAN", "ENUM", "OBJECT", "ARRAY", "JSON");

    /**
     * 校验动作输入契约本身是否有效。
     *
     * @param paramSchema 参数定义 JSON
     */
    public void validateSchema(String paramSchema) {
        schemaOf(paramSchema);
    }

    /**
     * 按动作输入契约展开默认值并校验参数。
     *
     * @param paramSchema 参数定义 JSON
     * @param paramsJson 本次输入参数 JSON
     * @return 可直接落命令快照和下发的完整参数 JSON；没有参数时返回 null
     */
    public String resolve(String paramSchema, String paramsJson) {
        JSONObject schema = schemaOf(paramSchema);
        JSONObject params = paramsOf(paramsJson);
        if (schema.isEmpty()) {
            return params.isEmpty() ? null : params.toString();
        }
        rejectUnknownParams(schema, params);
        for (String path : schema.keySet()) {
            JSONObject definition = schema.getJSONObject(path);
            Object value = valueAt(params, path);
            if (value == null && definition.containsKey("default")) {
                value = definition.get("default");
                putAt(params, path, value);
            }
            if (value == null) {
                if (definition.getBool("required", false)) {
                    throw invalid("缺少必填参数 " + path);
                }
                continue;
            }
            String type = typeOf(definition);
            validateValue(path, value, type);
            validateConstraints(path, value, definition.get("constraints"));
        }
        return params.isEmpty() ? null : params.toString();
    }

    /**
     * 将参数定义解析为对象，并检查每个字段定义。
     */
    private JSONObject schemaOf(String paramSchema) {
        if (StrUtil.isBlank(paramSchema)) {
            return new JSONObject();
        }
        final JSONObject schema;
        try {
            schema = JSONUtil.parseObj(paramSchema);
        } catch (RuntimeException ex) {
            throw schemaInvalid("必须是 JSON 对象");
        }
        for (String path : schema.keySet()) {
            if (StrUtil.isBlank(path)) {
                throw schemaInvalid("参数 " + path + " 的定义必须是 JSON 对象");
            }
            JSONObject definition = normalizeDefinition(schema.get(path));
            if (definition == null) {
                throw schemaInvalid("参数 " + path + " 的定义必须是 JSON 对象");
            }
            schema.set(path, definition);
            typeOf(definition);
        }
        return schema;
    }

    /**
     * 将历史标量简写转换为只带默认值的定义，兼容早期已保存的动作数据。
     *
     * <p>兼容时不推断或转换类型，避免改变最终发送给厂商的 JSON 值。</p>
     */
    private JSONObject normalizeDefinition(Object rawDefinition) {
        if (rawDefinition instanceof JSONObject definition) {
            return definition;
        }
        if (rawDefinition != null) {
            return legacyDefinition(rawDefinition);
        }
        return null;
    }

    /**
     * 创建带默认值的历史兼容定义。
     */
    private JSONObject legacyDefinition(Object defaultValue) {
        return new JSONObject().set("type", "JSON").set("default", defaultValue);
    }

    /**
     * 将本次参数解析为 JSON 对象。
     */
    private JSONObject paramsOf(String paramsJson) {
        if (StrUtil.isBlank(paramsJson)) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(paramsJson);
        } catch (RuntimeException ex) {
            throw invalid("必须是 JSON 对象");
        }
    }

    /**
     * 读取并校验参数类型声明。
     */
    private String typeOf(JSONObject definition) {
        String type = StrUtil.blankToDefault(definition.getStr("type"), "STRING").toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(type)) {
            throw schemaInvalid("不支持的参数类型 " + type);
        }
        return type;
    }

    /**
     * 拒绝输入契约未声明的顶层参数，避免拼写错误被静默下发。
     */
    private void rejectUnknownParams(JSONObject schema, JSONObject params) {
        Set<String> allowedRoots = new HashSet<>();
        schema.keySet().forEach(path -> allowedRoots.add(rootOf(path)));
        for (String key : params.keySet()) {
            if (!allowedRoots.contains(key)) {
                throw invalid("未定义参数 " + key);
            }
        }
    }

    /**
     * 取得点路径的顶层字段名。
     */
    private String rootOf(String path) {
        int dot = path.indexOf('.');
        int bracket = path.indexOf('[');
        int end = dot < 0 ? path.length() : dot;
        if (bracket >= 0 && bracket < end) {
            end = bracket;
        }
        return path.substring(0, end);
    }

    /**
     * 读取点路径参数；若输入显式使用完整路径名，则优先读取该键。
     */
    private Object valueAt(JSONObject params, String path) {
        return params.containsKey(path) ? params.get(path) : params.getByPath(path);
    }

    /**
     * 写入点路径参数；已有完整路径键时保持原有扁平结构。
     */
    private void putAt(JSONObject params, String path, Object value) {
        if (params.containsKey(path) || !path.contains(".")) {
            params.set(path, value);
            return;
        }
        String[] parts = path.split("\\.");
        JSONObject cursor = params;
        for (int i = 0; i < parts.length - 1; i++) {
            Object child = cursor.get(parts[i]);
            if (child == null) {
                JSONObject created = new JSONObject();
                cursor.set(parts[i], created);
                cursor = created;
            } else if (child instanceof JSONObject object) {
                cursor = object;
            } else {
                throw invalid("参数路径 " + path + " 与已有值冲突");
            }
        }
        cursor.set(parts[parts.length - 1], value);
    }

    /**
     * 按显式类型校验参数值，但不改变原始 JSON 类型和值。
     */
    private void validateValue(String path, Object value, String type) {
        boolean valid = switch (type) {
            case "STRING", "ENUM" -> value instanceof CharSequence;
            case "INTEGER" -> value instanceof Number number
                    && new BigDecimal(String.valueOf(number)).stripTrailingZeros().scale() <= 0;
            case "DECIMAL" -> value instanceof Number;
            case "BOOLEAN" -> value instanceof Boolean;
            case "OBJECT" -> value instanceof JSONObject;
            case "ARRAY" -> value instanceof JSONArray;
            case "JSON" -> true;
            default -> false;
        };
        if (!valid) {
            throw invalid("参数 " + path + " 不是有效的 " + type);
        }
    }

    /**
     * 校验枚举集合或数值范围约束。
     */
    private void validateConstraints(String path, Object value, Object constraints) {
        if (constraints instanceof JSONArray allowed && !allowed.isEmpty()) {
            boolean matched = allowed.stream().anyMatch(item -> String.valueOf(item).equals(String.valueOf(value)));
            if (!matched) {
                throw invalid("参数 " + path + " 不在允许值 " + allowed + " 中");
            }
            return;
        }
        if (!(constraints instanceof JSONObject range) || !(value instanceof Number)) {
            return;
        }
        BigDecimal actual = new BigDecimal(String.valueOf(value));
        if (range.get("min") != null && actual.compareTo(new BigDecimal(String.valueOf(range.get("min")))) < 0) {
            throw invalid("参数 " + path + " 不能小于 " + range.get("min"));
        }
        if (range.get("max") != null && actual.compareTo(new BigDecimal(String.valueOf(range.get("max")))) > 0) {
            throw invalid("参数 " + path + " 不能大于 " + range.get("max"));
        }
    }

    /**
     * 构造输入契约错误。
     */
    private RuntimeException schemaInvalid(String message) {
        return exception(DEVICE_ACTION_PARAM_SCHEMA_INVALID, message);
    }

    /**
     * 构造运行参数错误。
     */
    private RuntimeException invalid(String message) {
        return exception(DEVICE_ACTION_PARAM_INVALID, message);
    }
}
