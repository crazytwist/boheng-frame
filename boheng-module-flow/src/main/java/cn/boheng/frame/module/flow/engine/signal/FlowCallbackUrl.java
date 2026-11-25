package cn.boheng.frame.module.flow.engine.signal;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * 拼装给外部系统用的节点回调地址。
 *
 * <p>为什么需要这一层：令牌是按「执行 + 节点 + 尝试」派生的，服务端能重算，
 * 但对方算不出来。所以必须有人把带 token 的完整地址交到对方手上，
 * 否则回调入口形同虚设——对方知道该打哪个路径，却过不了令牌校验。</p>
 *
 * <p>和 {@code DevicePlatformUrl} 是两件不同的事：设备认命令号，回调进设备域；
 * 认流程的对方（LIMS / MES / 上游调度）直接回调节点，所以根地址和前缀也不同
 * （这里是 {@code /app-api}，那边是 {@code /admin-api}）。</p>
 */
@Component
public class FlowCallbackUrl {

    /**
     * 外部系统回调平台用的根地址，如 http://10.0.0.5:48080。
     * 不配时退回本机地址，只适合本机联调：对方拿到 127.0.0.1 会把回调打到自己身上，
     * 表现为对方「已回报」而流程永远等不到结果。
     */
    public static final String CALLBACK_BASE_PROPERTY = "boheng.flow.callback-base-url";

    /**
     * App 端接口前缀。回调入口是 @PermitAll 的 app 接口
     */
    private static final String APP_PREFIX = "/app-api";

    /**
     * 运行环境。端口在请求时再读，避免启动阶段还没有 local.server.port
     */
    @Resource
    private Environment environment;

    /**
     * 签发令牌。地址里的 token 与校验方共用同一实现，避免两边算法漂移
     */
    @Resource
    private FlowCallbackToken callbackToken;

    /**
     * 生成某个等待节点的完整回调地址：
     * {@code {base}/app-api/flow/external-callback/{executionId}/{nodeId}?attempt=&token=}
     */
    public String callbackUrl(Long executionId, String nodeId, int attempt) {
        return base() + "/flow/external-callback/"
                + executionId + "/" + UriUtils.encodePathSegment(nodeId, StandardCharsets.UTF_8)
                + "?attempt=" + attempt
                + "&token=" + callbackToken.issue(executionId, nodeId, attempt);
    }

    /**
     * 回调根地址（已拼上 /app-api），供排查时直接展示
     */
    public String base() {
        String root = StrUtil.trimToNull(environment.getProperty(CALLBACK_BASE_PROPERTY));
        return (root == null ? localRoot() : StrUtil.removeSuffix(root.trim(), "/")) + APP_PREFIX;
    }

    /**
     * 本机根地址。优先用实际监听端口
     */
    private String localRoot() {
        String local = environment.getProperty("local.server.port");
        if (StrUtil.isNotBlank(local)) {
            return "http://127.0.0.1:" + local;
        }
        return "http://127.0.0.1:" + environment.getProperty("server.port", "48080");
    }

}
