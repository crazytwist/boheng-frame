package cn.boheng.frame.module.device.service.action;

import cn.boheng.frame.module.device.service.template.MessageTemplateFunctions;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_TEMPLATE_INVALID;

/**
 * 设备动作报文映射的保存期校验。
 *
 * <p>模板里能引用的东西只有三类：输入契约声明过的参数、内置函数（如 ${uuid()}）、
 * 以及网关下发时注入的系统变量（commandNo / callbackUrl / callbackToken）。
 * 引用了别的东西，运行期才会以「存在未解析占位符」失败，那时候已经在设备面前了，
 * 所以把判断提前到保存动作的时刻。</p>
 *
 * @author yinan
 */
@Component
public class DeviceTemplateChecker {

    /**
     * 模板占位符 ${...}
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]*)}");

    /**
     * 函数调用，无参如 uuid()，带参如 randomHex(16)
     */
    private static final Pattern FUNCTION_CALL =
            Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^{}()]*)\\)$");

    /**
     * 网关下发时注入的变量，见 DeviceHttpGateway#invoke
     */
    private static final Set<String> SYSTEM_VARS = Set.of("commandNo", "callbackUrl", "callbackToken");

    /**
     * 校验报文映射与输入契约是否对得上。
     *
     * @param paramSchema 输入契约 JSON
     * @param requestTemplate 请求报文模板
     */
    public void check(String paramSchema, String requestTemplate) {
        if (StrUtil.isBlank(requestTemplate)) {
            return;
        }
        Set<String> declared = declaredRoots(paramSchema);
        Set<String> unknown = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(requestTemplate);
        while (matcher.find()) {
            String expr = StrUtil.trimToEmpty(matcher.group(1));
            if (expr.isEmpty()) {
                unknown.add("${}");
                continue;
            }
            Matcher function = FUNCTION_CALL.matcher(expr);
            if (function.matches()) {
                if (!MessageTemplateFunctions.isSupportedFunction(function.group(1))) {
                    unknown.add(expr + "（不支持的内置函数）");
                }
                continue;
            }
            // 兼容 ${$.param} 写法
            String path = expr.startsWith("$.") ? expr.substring(2) : expr;
            String root = DeviceParamResolver.rootOf(path);
            if (!declared.contains(root) && !SYSTEM_VARS.contains(root)) {
                unknown.add(path);
            }
        }
        if (!unknown.isEmpty()) {
            throw exception(DEVICE_ACTION_TEMPLATE_INVALID,
                    "报文映射引用了未声明的占位符 " + String.join("、", unknown)
                            + "；请先在输入契约中声明，或改用内置函数 ${uuid()}、${randomHex(16)}、系统变量 ${commandNo}");
        }
    }

    /**
     * 输入契约声明过的顶层参数名。
     */
    private Set<String> declaredRoots(String paramSchema) {
        Set<String> roots = new LinkedHashSet<>();
        if (StrUtil.isBlank(paramSchema)) {
            return roots;
        }
        try {
            JSONObject schema = JSONUtil.parseObj(paramSchema);
            schema.keySet().forEach(path -> roots.add(DeviceParamResolver.rootOf(path)));
        } catch (RuntimeException ignored) {
            // 契约本身不合法由 DeviceParamResolver 报错，这里不重复拦截
        }
        return roots;
    }

}
