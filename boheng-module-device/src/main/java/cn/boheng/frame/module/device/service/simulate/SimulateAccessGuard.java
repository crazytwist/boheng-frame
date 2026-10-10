package cn.boheng.frame.module.device.service.simulate;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 仿真设备接口的访问口令。
 *
 * 仿真接口免登录，只给本进程调用。口令在启动时随机生成、只存在内存里，外部请求拿不到，
 * 以此挡住借仿真接口探测设备配置、或让平台去请求任意地址。
 */
@Component
public class SimulateAccessGuard {

    /**
     * 携带口令的请求头
     */
    public static final String HEADER = "X-Simulate-Secret";

    /**
     * 本进程的口令
     */
    private final String secret;

    /**
     * 启动时生成 32 位十六进制口令
     */
    public SimulateAccessGuard() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        this.secret = HexUtil.encodeHexStr(bytes);
    }

    /**
     * 当前口令，平台调用仿真设备时放进请求头
     */
    public String secret() {
        return secret;
    }

    /**
     * 请求带的口令是否正确，比较耗时与内容无关
     */
    public boolean allows(String presented) {
        if (StrUtil.isBlank(presented)) {
            return false;
        }
        return MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

}
