package cn.boheng.frame.module.device.service.callback;

import cn.boheng.frame.framework.tenant.core.util.TenantUtils;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.mysql.command.DeviceCommandMapper;
import cn.boheng.frame.module.device.service.command.DeviceCommandService;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;

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
     * 平台地址
     */
    @Resource
    private DevicePlatformUrl platformUrl;

    /**
     * 签发这条命令的回调地址和令牌。仿真设备在本进程，台账没配地址时用本机地址。
     */
    public DeviceCallbackTicket issue(DeviceInfoDO device, DeviceActionDO action, String commandNo, boolean simulated) {
        int timeoutSec = timeoutSec(device, action);
        String token = newToken();
        String template = StrUtil.trimToNull(device.getCallbackUrl());
        if (template == null) {
            String base = simulated ? platformUrl.localAdminBase() : platformUrl.callbackAdminBase();
            template = base + "/device/callback/${commandNo}?token=${callbackToken}";
        }
        String url = template
                .replace("${commandNo}", DevicePlatformUrl.encode(commandNo))
                .replace("${callbackToken}", token)
                .replace("${deviceCode}", DevicePlatformUrl.encode(device.getDeviceCode()));
        return new DeviceCallbackTicket(commandNo, token, hash(token), url, timeoutSec,
                LocalDateTime.now().plusSeconds(timeoutSec));
    }

    /**
     * 这次最多等多久：动作上的优先，其次台账，再没有用默认值。超出范围的收进 1~600 秒。
     */
    public int timeoutSec(DeviceInfoDO device, DeviceActionDO action) {
        Integer value = action == null ? null : action.getCallbackTimeoutSec();
        if (value == null && device != null) {
            value = device.getCallbackTimeoutSec();
        }
        if (value == null) {
            return DEFAULT_TIMEOUT_SEC;
        }
        return Math.max(1, Math.min(value, MAX_TIMEOUT_SEC));
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
     * 32 位十六进制随机令牌
     */
    private static String newToken() {
        byte[] bytes = new byte[16];
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
