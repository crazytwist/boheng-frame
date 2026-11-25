package cn.boheng.frame.module.device.service.callback;

import cn.boheng.frame.framework.tenant.core.util.TenantUtils;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.mysql.action.DeviceActionMapper;
import cn.boheng.frame.module.device.dal.mysql.command.DeviceCommandMapper;
import cn.boheng.frame.module.device.service.command.DeviceCommandService;
import cn.boheng.frame.module.device.service.result.DeviceResultJudge;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONNull;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * 设备指令回调。发命令前签发回调地址和令牌，设备回调时核对令牌并把结果交给命令记录。
 */
@Service
@Slf4j
public class DeviceCallbackService {

    /**
     * 动作和台账都没配时，默认等 300 秒
     */
    public static final int DEFAULT_TIMEOUT_SEC = 300;

    /**
     * 显式配成 0（或负数）表示不超时：平台一直等，`timeout_at` 留空。
     *
     * <p>超时的判断交给上层的流程节点（{@code flow_step.timeout_ms}），
     * 设备域不替业务决定「等多久算失败」。这类命令不会被超时扫描收口，
     * 回调一直不来时只能人工结束（{@code /admin-api/device/command/close}）。</p>
     */
    public static final int NO_TIMEOUT_SEC = 0;

    /**
     * 回调截止时间上限，防止异常配置让命令长期占用设备
     */
    public static final int MAX_TIMEOUT_SEC = 600;

    /**
     * 发给设备的命令号请求头
     */
    public static final String COMMAND_HEADER = "X-Command-No";

    /**
     * 发给设备的回调地址请求头
     */
    public static final String CALLBACK_URL_HEADER = "X-Callback-Url";

    /**
     * 回调令牌请求头。回调地址里没带 token 时，设备可以用这个头回传
     */
    public static final String TOKEN_HEADER = "X-Callback-Token";

    /**
     * 生成令牌用的随机源
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 固定地址回调里最多拿多少个值去比对。关联键只有一个，
     * 取多了只是浪费查询，还给了伪造回调更大的试探面。
     */
    private static final int MAX_CORRELATE_CANDIDATES = 16;

    /**
     * 参与比对的值的最大长度，与 device_command.correlate_value 列宽一致
     */
    private static final int MAX_CORRELATE_LENGTH = 128;

    /**
     * 能安全用于报文内容兜底匹配的字符集与长度。
     *
     * <p>长度下限很关键：回调报文里什么短值都有（{@code ExecuteStatus:0}、{@code code:0}），
     * 拿它们去 LIKE 报文内容会误命中——UUID 里本来就含数字。只有 UUID（36）、
     * 十六进制流水号（16）这类才够长认人，所以压到 8 位起步。</p>
     *
     * <p>字符集限定为 {@code %}、{@code _} 之外的安全字符，避免值被当成模式串扫全表。</p>
     */
    private static final Pattern SAFE_CORRELATE = Pattern.compile("[A-Za-z0-9_-]{8,128}");

    /**
     * 命令表。回调没有租户头，按命令号查时要先忽略租户
     */
    @Resource
    private DeviceCommandMapper deviceCommandMapper;

    /**
     * 命令记录服务
     */
    @Resource
    private DeviceCommandService deviceCommandService;

    /**
     * 动作表。判定「这次回调是不是终态」要看动作上的 result_rule
     */
    @Resource
    private DeviceActionMapper deviceActionMapper;

    /**
     * 应答判定器。回调的终态判断与成败判定都收在这里
     */
    @Resource
    private DeviceResultJudge deviceResultJudge;

    /**
     * 平台地址
     */
    @Resource
    private DevicePlatformUrl platformUrl;

    /**
     * 回环地址告警是否已经打过。
     *
     * <p>漏配 {@code callback-base-url} 是部署级问题，一条 WARN 就够定位；
     * 每条命令都打会把业务日志淹掉，反而看不出真正的问题。</p>
     */
    private final AtomicBoolean loopbackWarned = new AtomicBoolean(false);

    /**
     * 签发这条命令的回调地址和令牌。仿真设备在本进程，台账没配地址时用本机地址。
     *
     * <p>默认地址是 {@code /api/callback/{commandNo}?token=}，不带 admin 前缀：
     * 回调入口在 {@code controller.open} 包下，设备是外部系统，不该被要求走管理端路径。</p>
     */
    public DeviceCallbackTicket issue(DeviceInfoDO device, DeviceActionDO action, String commandNo, boolean simulated) {
        int timeoutSec = timeoutSec(device, action);
        String token = newToken();
        String template = StrUtil.trimToNull(device.getCallbackUrl());
        boolean loopbackForRealDevice = false;
        if (template == null) {
            String base = simulated ? platformUrl.localRootBase() : platformUrl.callbackRootBase();
            template = base + "/api/callback/${commandNo}?token=${callbackToken}";
            loopbackForRealDevice = !simulated && !platformUrl.callbackBaseConfigured();
        }
        String url = template
                .replace("${commandNo}", DevicePlatformUrl.encode(commandNo))
                .replace("${callbackToken}", token)
                .replace("${deviceCode}", DevicePlatformUrl.encode(device.getDeviceCode()));
        if (loopbackForRealDevice && loopbackWarned.compareAndSet(false, true)) {
            // 只提示一次：这是部署配置缺失，不是这条命令的问题。
            // 设备走固定地址回调（HPLC 的 /api/OnResponse、AGV 的 /api/agv/callback，地址由厂商写死）
            // 时这个地址根本用不上，所以措辞里要说明可忽略，否则会把人往错误的排查方向带。
            log.warn("[issue][未配置 {}，下发给设备({}) 的回调地址是平台本机回环：{}；"
                            + "设备若按固定地址回调可忽略，否则请配置为设备能访问到的平台地址]",
                    DevicePlatformUrl.CALLBACK_BASE_PROPERTY, device.getDeviceCode(), url);
        }
        // 不超时时不给截止时刻：扫描器按 timeout_at 判超时，留空就不会被它收口
        LocalDateTime timeoutAt = timeoutSec <= NO_TIMEOUT_SEC ? null : LocalDateTime.now().plusSeconds(timeoutSec);
        return new DeviceCallbackTicket(commandNo, token, hash(token), url, timeoutSec, timeoutAt);
    }

    /**
     * 这次最多等多久：动作上的优先，其次台账，再没有用默认值。
     *
     * <p>配成 0 或负数表示不超时，原样返回 {@value #NO_TIMEOUT_SEC}；
     * 正数收进 1~{@value #MAX_TIMEOUT_SEC} 秒。</p>
     */
    public int timeoutSec(DeviceInfoDO device, DeviceActionDO action) {
        Integer value = action == null ? null : action.getCallbackTimeoutSec();
        if (value == null && device != null) {
            value = device.getCallbackTimeoutSec();
        }
        if (value == null) {
            return DEFAULT_TIMEOUT_SEC;
        }
        if (value <= NO_TIMEOUT_SEC) {
            return NO_TIMEOUT_SEC;
        }
        return Math.min(value, MAX_TIMEOUT_SEC);
    }

    /**
     * 收下一次设备回调。令牌对得上、命令还在等，才写入结果。
     */
    public DeviceCallbackReceipt receive(String commandNo, String token, String body, String contentType) {
        if (StrUtil.isBlank(commandNo)) {
            return new DeviceCallbackReceipt(400, "缺少命令号 commandNo", null);
        }
        DeviceCommandDO command = TenantUtils.executeIgnore(() -> deviceCommandMapper.selectByCommandNo(commandNo.trim()));
        if (command == null) {
            return new DeviceCallbackReceipt(404, "命令不存在", commandNo);
        }
        if (!matches(command.getCallbackTokenHash(), token)) {
            log.warn("[receive][命令({}) 回调令牌不对]", commandNo);
            return new DeviceCallbackReceipt(403, "回调令牌不对", commandNo);
        }
        if (!terminal(command, body)) {
            return pendingReceipt(command);
        }
        Long tenantId = command.getTenantId();
        boolean accepted = TenantUtils.execute(tenantId,
                () -> deviceCommandService.completeCallback(command.getId(), body, contentType));
        if (!accepted) {
            String status = TenantUtils.execute(tenantId, () -> deviceCommandService.getStatus(command.getId()));
            return new DeviceCallbackReceipt(409, "命令已结束（" + status + "），本次回调没有写入", commandNo);
        }
        log.info("[receive][设备({}) 命令({}) 回调已收下]", command.getDeviceCode(), commandNo);
        return new DeviceCallbackReceipt(200, "ok", commandNo);
    }

    /**
     * 收下一次「固定地址」回调。
     *
     * <p>这类设备（如海康 RCS、HPLC）的回调地址在厂商侧写死：报文里不带我们的命令号，
     * 也没地方放带令牌的回调地址。只能把请求报文里所有可能的值拿去认命令。</p>
     *
     * <p>认两轮：</p>
     * <ol>
     *   <li>精确匹配 {@code correlate_value}（走索引）——正常情况命中在这里</li>
     *   <li>报文内容兜底——设备可能回的是另一个等价字段。比如 HPLC 的请求里
     *       EventId 和 RequestId 都是我们现生成的，关联键列只存得下一个，
     *       它回调时回哪个都不一定，另一个只能按报文全文找</li>
     * </ol>
     *
     * <p>安全边界：没有一次性令牌，靠关联键的随机性（UUID / 16 位十六进制）
     * 和「只匹配 WAITING 状态」两道限制。要更强的保证，得让厂商把命令号带回来。</p>
     *
     * @param body 回调原始报文
     * @param contentType 厂商声明的类型
     * @return 处理结果；没匹配到时返回 404，让厂商知道这次没生效
     */
    public DeviceCallbackReceipt receiveByCorrelate(String body, String contentType) {
        List<String> candidates = correlateCandidates(body);
        if (candidates.isEmpty()) {
            return new DeviceCallbackReceipt(400, "回调报文里没有可识别的关联字段", null);
        }
        for (String candidate : candidates) {
            DeviceCommandDO command = TenantUtils.executeIgnore(
                    () -> deviceCommandMapper.selectWaitingByCorrelateValue(candidate));
            DeviceCallbackReceipt receipt = settle(command, candidate, body, contentType);
            if (receipt != null) {
                return receipt;
            }
        }
        for (String candidate : candidates) {
            if (!SAFE_CORRELATE.matcher(candidate).matches()) {
                // 太短（ExecuteStatus 的 0、code 的 0）或含通配符的值不进 LIKE，
                // 否则短值会误命中、通配符会扫全表
                continue;
            }
            DeviceCommandDO command = TenantUtils.executeIgnore(
                    () -> deviceCommandMapper.selectWaitingByPayloadContains(candidate));
            DeviceCallbackReceipt receipt = settle(command, candidate, body, contentType);
            if (receipt != null) {
                return receipt;
            }
        }
        return new DeviceCallbackReceipt(404, "没有匹配到等待中的命令", null);
    }

    /**
     * 按指定的关联键值认命令。
     *
     * <p>给「知道该用哪个字段」的调用方用（AGV 的 {@code reqCode}）：直接按关联键列精确匹配，
     * 走索引，也不会碰报文内容兜底那条 LIKE。定位不到时退回
     * {@link #receiveByCorrelate}，避免因为「关联键还没写进库」或「动作配的关联键不是这个字段」
     * 把厂商一条正常回调丢掉。</p>
     *
     * @param key 关联键值（下发时从请求报文里取的那个字段，如 reqCode）
     * @param body 回调原始报文
     * @param contentType 厂商声明的类型
     * @return 处理结果
     */
    public DeviceCallbackReceipt receiveByCorrelateKey(String key, String body, String contentType) {
        String value = StrUtil.trimToNull(key);
        if (value == null) {
            // 报文里连关联键都没有，只能按全文去找
            return receiveByCorrelate(body, contentType);
        }
        DeviceCommandDO command = TenantUtils.executeIgnore(
                () -> deviceCommandMapper.selectWaitingByCorrelateValue(value));
        DeviceCallbackReceipt receipt = settle(command, value, body, contentType);
        if (receipt != null) {
            return receipt;
        }
        log.info("[receiveByCorrelateKey][关联键({}) 没命中等待中的命令，退回按报文内容认人]", value);
        return receiveByCorrelate(body, contentType);
    }

    /**
     * 定位到命令后结算。没定位到返回 null，让调用方继续找下一个候选值。
     */
    private DeviceCallbackReceipt settle(DeviceCommandDO command, String candidate, String body, String contentType) {
        if (command == null) {
            return null;
        }
        if (!terminal(command, body)) {
            // 定位到了，但这条报文只是任务过程中的一站（AGV 的 method 不是 end）。
            // 不结算、也不换下一个候选值——命令已经认出来了，再找下去只会重复判断。
            return pendingReceipt(command);
        }
        Long tenantId = command.getTenantId();
        boolean accepted = TenantUtils.execute(tenantId,
                () -> deviceCommandService.completeCallback(command.getId(), body, contentType));
        if (!accepted) {
            // 命中了但已经不是等待态：多半是并发收到了两次，或者刚刚超时收口
            return new DeviceCallbackReceipt(409, "命令已结束，本次回调没有写入", command.getCommandNo());
        }
        log.info("[receiveByCorrelate][设备({}) 命令({}) 按关联键({}) 收下回调]",
                command.getDeviceCode(), command.getCommandNo(), candidate);
        return new DeviceCallbackReceipt(200, "ok", command.getCommandNo());
    }

    /**
     * 这次回调能不能结算命令。
     *
     * <p>回调不等于结果：AGV（海康 RCS）会在任务过程中推多条回调，用 {@code method} 区分阶段，
     * 只有 {@code end} 才是结束。规则配在动作的 {@code result_rule} 里
     * （{@code settlePath} / {@code settleValues}），没配就照旧「收到即结算」。</p>
     */
    private boolean terminal(DeviceCommandDO command, String body) {
        boolean terminal = deviceResultJudge.isTerminal(actionOf(command), body);
        if (!terminal) {
            log.info("[terminal][设备({}) 命令({}) 收到的回调还没到终态，命令继续等待]",
                    command.getDeviceCode(), command.getCommandNo());
        }
        return terminal;
    }

    /**
     * 命令对应的动作定义。查不到时返回空，判定器按「没配规则」处理，等价于收到即结算
     */
    private DeviceActionDO actionOf(DeviceCommandDO command) {
        if (StrUtil.isBlank(command.getDeviceTypeCode()) || StrUtil.isBlank(command.getActionCode())) {
            return null;
        }
        return TenantUtils.executeIgnore(() -> deviceActionMapper.selectByTypeAndAction(
                command.getDeviceTypeCode(), command.getActionCode()));
    }

    /**
     * 收到了但还不能结算时的答复：回 200 让设备别重发，状态说明写在 msg 里
     */
    private static DeviceCallbackReceipt pendingReceipt(DeviceCommandDO command) {
        return new DeviceCallbackReceipt(200, "已收到，任务尚未结束，继续等待", command.getCommandNo());
    }

    /**
     * 从回调报文里取出所有可能当关联键的值。
     *
     * <p>不按字段名挑：厂商把 reqCode 放在顶层还是埋在 Parameters 里都认，
     * 反正关联键是我们自己生成的高熵串，撞不上。深度和个数都设上限，
     * 免得厂商回一个超大报文把库扫一遍。</p>
     */
    private static List<String> correlateCandidates(String body) {
        String trimmed = StrUtil.trimToNull(body);
        if (trimmed == null) {
            return List.of();
        }
        final JSONObject payload;
        try {
            payload = JSONUtil.parseObj(trimmed);
        } catch (RuntimeException ex) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        collectCandidates(payload, values, 0);
        return values;
    }

    /**
     * 递归收集标量值，最多两层、最多 {@link #MAX_CORRELATE_CANDIDATES} 个
     */
    private static void collectCandidates(Object node, List<String> out, int depth) {
        if (depth > 2 || out.size() >= MAX_CORRELATE_CANDIDATES) {
            return;
        }
        if (node instanceof JSONObject object) {
            for (String key : object.keySet()) {
                collectCandidates(object.get(key), out, depth + 1);
            }
            return;
        }
        if (node instanceof JSONArray array) {
            for (Object item : array) {
                collectCandidates(item, out, depth + 1);
            }
            return;
        }
        if (node == null || node instanceof JSONNull) {
            return;
        }
        String text = StrUtil.trimToNull(String.valueOf(node));
        // 关联键就那么长，超出的当正文内容，不参与匹配
        if (text != null && text.length() <= MAX_CORRELATE_LENGTH && !out.contains(text)) {
            out.add(text);
        }
    }

    /**
     * 32 位十六进制随机令牌
     */
    private static String newToken() {        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexUtil.encodeHexStr(bytes);
    }

    /**
     * 令牌摘要
     */
    private static String hash(String token) {
        return DigestUtil.sha256Hex(token);
    }

    /**
     * 按摘要核对令牌，比较时长短不泄露
     */
    private static boolean matches(String expectHash, String token) {
        if (StrUtil.isBlank(expectHash) || StrUtil.isBlank(token)) {
            return false;
        }
        return MessageDigest.isEqual(expectHash.getBytes(StandardCharsets.UTF_8),
                hash(token.trim()).getBytes(StandardCharsets.UTF_8));
    }

}
