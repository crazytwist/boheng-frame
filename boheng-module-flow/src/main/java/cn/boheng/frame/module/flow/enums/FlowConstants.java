package cn.boheng.frame.module.flow.enums;

/**
 * 流程定义里用到的固定取值。当前只开放直线设备步骤，所以类型和失败策略先写死在这里。
 */
public final class FlowConstants {

    private FlowConstants() {
    }

    /** 草稿，可以改节点 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 已发布，节点和校验和不再变 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 已停用。历史运行以后仍可引用，新的运行不再选用 */
    public static final String STATUS_RETIRED = "RETIRED";

    /** 当前唯一允许的步骤类型：调用一次设备动作 */
    public static final String STEP_DEVICE_COMMAND = "device.command";

    /** 次数用尽后整条流程失败。当前没有“跳过失败节点” */
    public static final String FAIL_FAST = "FAIL_FAST";

    /** 启动流程时由调用方传入 */
    public static final String SOURCE_INPUT = "INPUT";

    /** 紧挨着的上一步输出 */
    public static final String SOURCE_NODE = "NODE";

    /** 编排时写死的值 */
    public static final String SOURCE_LITERAL = "LITERAL";

    /** 没填超时时使用。和设备回调的默认等待一致 */
    public static final long DEFAULT_TIMEOUT_MS = 300_000L;

    /** 最短等待。短于 1 秒分不清是配置错误还是真超时 */
    public static final long MIN_TIMEOUT_MS = 1_000L;

    /** 最长等待 24 小时。再长应拆成定时步骤，而不是把一条命令挂住 */
    public static final long MAX_TIMEOUT_MS = 86_400_000L;

    /** 含首次。默认 1，避免还没做运行时就让人以为会自动重发三次 */
    public static final int DEFAULT_MAX_ATTEMPTS = 1;

    /** 重发是物理动作，先把上限收在 5 次 */
    public static final int MAX_ATTEMPTS_LIMIT = 5;

    /** 一条直线实验不会有几十步。上限用来拦住编辑器误操作 */
    public static final int MAX_NODES = 30;

    /** 步骤编码和节点编码：小写开头，方便出现在日志里 */
    public static final String NODE_ID_REGEX = "^[a-z][a-z0-9_]{0,31}$";

    /**
     * 状态的中文，用在“不能修改”的提示里。
     */
    public static String statusLabel(String status) {
        if (STATUS_PUBLISHED.equals(status)) {
            return "发布";
        }
        if (STATUS_RETIRED.equals(status)) {
            return "停用";
        }
        return "锁定";
    }

}
