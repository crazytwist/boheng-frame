package cn.boheng.frame.module.flow.engine.signal;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 节点级外部回调的令牌。
 *
 * <p>用「流程执行 + 节点 + 尝试次数」派生，服务端可重算，所以不必把令牌存库，
 * 也不会像随机令牌那样必须先把地址下发给对方才能调用。代价是密钥必须配置，
 * 换密钥等于让在途等待的回调全部失效——这是有意的：宁可让它们超时重来。</p>
 */
@Component
public class FlowCallbackToken {

    /**
     * 派生令牌用的密钥。生产环境必须在配置里显式指定
     */
    @Value("${boheng.flow.callback-secret:boheng-flow-callback-secret}")
    private String secret;

    /**
     * 签发令牌。同一执行同一节点同一次尝试恒定，重试（attempt 递增）会得到新令牌
     */
    public String issue(Long executionId, String nodeId, int attempt) {
        String raw = secret + ":" + executionId + ":" + nodeId + ":" + attempt;
        return DigestUtil.sha256Hex(raw).substring(0, 32);
    }

    /**
     * 校验令牌。常量时间比较，长度不一致直接失败
     */
    public boolean matches(Long executionId, String nodeId, Integer attempt, String token) {
        if (executionId == null || StrUtil.isBlank(nodeId) || attempt == null || StrUtil.isBlank(token)) {
            return false;
        }
        String expect = issue(executionId, nodeId, attempt);
        return MessageDigest.isEqual(expect.getBytes(StandardCharsets.UTF_8),
                token.trim().getBytes(StandardCharsets.UTF_8));
    }

}
