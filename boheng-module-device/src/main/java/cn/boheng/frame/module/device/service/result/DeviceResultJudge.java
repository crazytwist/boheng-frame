package cn.boheng.frame.module.device.service.result;

import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static cn.boheng.frame.module.device.enums.DeviceEnums.ErrorTypeEnum;

/**
 * 设备应答判定器：把厂商响应体翻译成「成功 / 失败 + 什么性质 + 值不值得重试」。
 *
 * <p>这是设备域唯一判成败的地方。判定规则挂在动作的 result_rule 上，
 * 按字段路径与取值比较，不引入表达式引擎——厂商换 ResultCode 规范时只改配置。</p>
 *
 * <p>规则结构（全部可选，缺省时回落到通用字段）：</p>
 * <pre>
 * {
 *   "successStatusPath": "$.ExecuteStatus",      // 执行状态字段
 *   "successStatusValues": [0],                  // 视为成功的取值
 *   "successCodePath": "$.ResultCode",           // 结果码字段
 *   "successCodeValues": ["00000000"],           // 视为成功的结果码
 *   "messagePath": "$.Message",                  // 失败说明字段
 *   "retryHintsPath": "$.Parameters.HandleCommands"  // 容错能力声明，含 Retry 才可重试
 * }
 * </pre>
 *
 * @author yinan
 */
@Component
@Slf4j
public class DeviceResultJudge {

    /**
     * 未配置规则时用来识别执行状态的通用字段
     */
    private static final String FALLBACK_STATUS_PATH = "ExecuteStatus";

    /**
     * 已经提示过「报文里没有规则要求的字段」的「动作|路径」，避免每条回调刷一行 WARN。
     * 进程内去重即可：重启后重新提示一次无伤大雅。
     */
    private final Set<String> missingFieldWarned = ConcurrentHashMap.newKeySet();

    /**
     * 判定一次应答。
     *
     * @param action 动作定义，可为空（无规则时走通用字段）
     * @param body 响应正文
     * @param httpStatus HTTP 状态码，可为空
     * @return 判定结论
     */
    public ResultVerdict judge(DeviceActionDO action, String body, Integer httpStatus) {
        if (httpStatus != null && (httpStatus < 200 || httpStatus >= 300)) {
            return ResultVerdict.failed(ErrorTypeEnum.TRANSPORT.getType(), Boolean.TRUE,
                    "设备返回 HTTP " + httpStatus + "：" + abbreviate(body));
        }
        JSONObject json = objectOf(body);
        if (json == null) {
            // 纯文本/XML 响应交给解析规则，不在这里判成败
            return ResultVerdict.ok();
        }
        JSONObject rule = ruleOf(action);
        boolean success = rule.isEmpty() ? fallbackSuccess(json) : ruleSuccess(rule, json, sceneOf(action));
        if (success) {
            return ResultVerdict.ok();
        }
        return ResultVerdict.failed(ErrorTypeEnum.DEVICE_REJECTED.getType(),
                retryableOf(rule, json), failureMessage(rule, json));
    }

    /**
     * 这次回调是不是「终态」，可以结算命令。
     *
     * <p>回调不等于结果。AGV（海康 RCS）一类设备会在任务执行过程中推多条回调，
     * 报文里用 {@code method} 区分阶段（{@code end} 才是结束）。如果收到第一条就结算，
     * 后面真正的结束报文会因为命令已经结束而被丢掉，流程会带着「车还在跑」的结果往下走。</p>
     *
     * <p>规则（配在 {@code result_rule} 里，全部可选）：</p>
     * <pre>
     * {
     *   "settlePath": "$.method",      // 判断阶段的字段
     *   "settleValues": ["end"]        // 只有这些值算终态
     * }
     * </pre>
     *
     * <p>不配 {@code settlePath}、报文里没有该字段、字段值为空时都按终态处理——
     * 宁可结算也不要因为配置不全把回调卡住（这会和「回调不超时」叠加成永久挂起）。</p>
     *
     * @param action 动作定义，可为空
     * @param body 回调正文
     * @return true 表示可以把命令结算掉
     */
    public boolean isTerminal(DeviceActionDO action, String body) {
        JSONObject rule = ruleOf(action);
        String path = StrUtil.trimToNull(rule.getStr("settlePath"));
        if (path == null) {
            return true;
        }
        List<String> settleValues = valuesOf(rule.get("settleValues"));
        if (settleValues.isEmpty()) {
            return true;
        }
        JSONObject json = objectOf(body);
        if (json == null) {
            return true;
        }
        String actual = text(valueAt(json, path));
        if (StrUtil.isBlank(actual)) {
            return true;
        }
        if (settleValues.contains(actual)) {
            return true;
        }
        log.info("[isTerminal][{}={} 不在终态 {} 里，本次回调只记录不结算]", path, actual, settleValues);
        return false;
    }

    /**
     * 按配置的规则判定。
     *
     * <p>规则里的每个取值条件都要求「字段在、值在允许集里」才算不通过；
     * 字段压根不在报文里时按未知放行（见 {@link #matches}）。
     * 这样同一份 {@code result_rule} 既能判受理应答，也能判最终回调——
     * 两者结构常常不同（AGV 的受理应答有 code，回调报文本不一定有）。</p>
     */
    private boolean ruleSuccess(JSONObject rule, JSONObject json, String scene) {
        return matches(rule, json, "successStatusValues", "successStatusPath", scene)
                && matches(rule, json, "successCodeValues", "successCodePath", scene);
    }

    /**
     * 一个取值条件是否通过
     *
     * @param valuesKey 允许取值列表在规则里的键名
     * @param pathKey 取值路径在规则里的键名
     * @param scene 动作标识，仅用于日志定位
     */
    private boolean matches(JSONObject rule, JSONObject json, String valuesKey, String pathKey, String scene) {
        List<String> allowed = valuesOf(rule.get(valuesKey));
        if (allowed.isEmpty()) {
            return true;
        }
        String path = rule.getStr(pathKey);
        Object raw = valueAt(json, path);
        String actual = text(raw);
        if (StrUtil.isBlank(actual)) {
            // 报文里没有这个字段：规则没命中不等于设备拒绝。宁可漏判也不要冤判——
            // 冤判会把「已经成功」的回调改成失败，而且现场只会看到「设备拒绝」这种假结论。
            //
            // 这是常态而不是异常：同一份 result_rule 要同时判受理应答和最终回调，
            // 两者字段集本来就不同（AGV 受理应答有 code，第二次回调没有），
            // 所以每条回调都会走到这里。按「动作 + 路径」只提示一次：
            // 既不会把日志刷满，路径真写错了也还有一行可查。
            warnOnceOnMissingField(scene, path);
            return true;
        }
        return allowed.contains(actual);
    }

    /**
     * 同一动作的同一路径缺字段，一个进程内只提示一次。
     *
     * <p>只提示一次是因为「回调报文里没有 ack 才有的字段」是完全正常的，
     * 重复提示只会把日志刷成噪音；而路径写错时第一条就够了。</p>
     */
    private void warnOnceOnMissingField(String scene, String path) {
        if (missingFieldWarned.add(scene + "|" + path)) {
            log.warn("[ruleSuccess][动作({}) 的 result_rule 要求 {}，但报文里没有该字段，本次跳过这条判定。"
                    + "若该动作的所有报文都没有这个字段，请核对 result_rule；同一动作只提示一次]", scene, path);
        }
    }

    /**
     * 动作标识，只用于日志
     */
    private static String sceneOf(DeviceActionDO action) {
        if (action == null) {
            return "-";
        }
        return StrUtil.blankToDefault(action.getDeviceTypeCode(), "-") + "."
                + StrUtil.blankToDefault(action.getActionCode(), "-");
    }

    /**
     * 没有规则时的兼容判定：先认旧的 status / success 字段，再认 ExecuteStatus。
     */
    private boolean fallbackSuccess(JSONObject json) {
        String status = json.getStr("status");
        if (StrUtil.isNotBlank(status)) {
            boolean failed = "FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status)
                    || "FAIL".equalsIgnoreCase(status) || Boolean.FALSE.equals(json.getBool("success"));
            return !failed;
        }
        Object executeStatus = valueAt(json, FALLBACK_STATUS_PATH);
        if (executeStatus != null) {
            log.debug("[judge][未配置 result_rule，按 ExecuteStatus 兜底判定: {}]", executeStatus);
            return "0".equals(text(executeStatus));
        }
        return true;
    }

    /**
     * 设备声明的可重试性。只有明确声明了 Retry 才返回 true；没声明返回 null 交给流程决定。
     */
    private Boolean retryableOf(JSONObject rule, JSONObject json) {
        Object hints = valueAt(json, rule.getStr("retryHintsPath"));
        if (!(hints instanceof JSONArray array)) {
            return null;
        }
        for (Object hint : array) {
            if ("Retry".equalsIgnoreCase(text(hint))) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    /**
     * 失败说明：优先取报文里的说明字段，其次带上结果码
     */
    private String failureMessage(JSONObject rule, JSONObject json) {
        StringBuilder message = new StringBuilder("设备拒绝执行");
        String detail = text(valueAt(json, rule.getStr("messagePath")));
        if (StrUtil.isNotBlank(detail)) {
            message.append("：").append(detail);
        }
        String code = text(valueAt(json, rule.getStr("successCodePath")));
        if (StrUtil.isNotBlank(code)) {
            message.append("（ResultCode=").append(code).append("）");
        }
        return message.toString();
    }

    /**
     * 读取动作上的判定规则；为空或格式不对时返回空对象，走兼容判定
     */
    private JSONObject ruleOf(DeviceActionDO action) {
        String raw = action == null ? null : action.getResultRule();
        if (StrUtil.isBlank(raw)) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(raw);
        } catch (RuntimeException ex) {
            log.warn("[judge][动作({}) 的 result_rule 不是合法 JSON，按兼容逻辑判定]", action.getActionCode());
            return new JSONObject();
        }
    }

    /**
     * 正文解析成对象；不是 JSON 对象时返回 null
     */
    private JSONObject objectOf(String body) {
        if (StrUtil.isBlank(body)) {
            return null;
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) {
            return null;
        }
        try {
            return JSONUtil.parseObj(trimmed);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 按路径取值，支持 $.a.b 与 a.b 两种写法
     */
    private static Object valueAt(JSONObject json, String path) {
        if (StrUtil.isBlank(path)) {
            return null;
        }
        String pointer = path.trim();
        if ("$".equals(pointer)) {
            return json;
        }
        if (pointer.startsWith("$.")) {
            pointer = pointer.substring(2);
        }
        try {
            return json.getByPath(pointer);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 允许的取值列表
     */
    private static List<String> valuesOf(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof JSONArray array) {
            for (Object item : array) {
                String value = text(item);
                if (StrUtil.isNotBlank(value)) {
                    values.add(value);
                }
            }
            return values;
        }
        String value = text(raw);
        if (StrUtil.isNotBlank(value)) {
            values.add(value);
        }
        return values;
    }

    /**
     * 统一成可比较的文本。数字去掉多余的尾零，避免 0 与 0.0 判不相等。
     */
    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value).trim();
    }

    /**
     * 截断正文，避免错误信息过长
     */
    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.trim();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "...";
    }

}
