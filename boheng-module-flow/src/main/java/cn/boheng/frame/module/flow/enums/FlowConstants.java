package cn.boheng.frame.module.flow.enums;

/**
 * 流程定义里用到的固定取值。步骤类型本身是开放的注册表
 * （见 engine/type/StepTypeRegistry），这里只放内置类型的 key 和通用上下限。
 */
public final class FlowConstants {

    private FlowConstants() {
    }

    // ==================== 流程版本状态 ====================

    /** 草稿，可以改节点 */
    public static final String STATUS_DRAFT = "DRAFT";

    /** 已发布，节点和校验和不再变 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 已停用。历史运行以后仍可引用，新的运行不再选用 */
    public static final String STATUS_RETIRED = "RETIRED";

    // ==================== 内置步骤类型 key ====================
    // 完整清单见 engine/type/builtin 与 engine/type/device。新增类型只注册，不改这里的核心逻辑。

    /** 调用一次设备动作 */
    public static final String STEP_DEVICE_COMMAND = "device.command";

    /** 调用一个白名单 Spring Bean */
    public static final String STEP_BEAN_INVOKE = "bean.invoke";

    /** 求值一段表达式（aviator），产出写进上下文 */
    public static final String STEP_SCRIPT_RUN = "script.run";

    /** 等人工在页面上确认 */
    public static final String STEP_MANUAL_TASK = "manual.task";

    /** 发一条通知 */
    public static final String STEP_NOTIFICATION_SEND = "notification.send";

    /** 出口路由：按属性值选一条分支往下走 */
    public static final String STEP_ROUTE = "route";

    /** 定时等待 */
    public static final String STEP_TIMER_WAIT = "timer.wait";

    /** 循环容器：把一段子图跑 N 轮 */
    public static final String STEP_LOOP = "loop";

    /** 子流程：SINGLE 引用一个子流程；FANOUT 按计划扇出多个子执行 */
    public static final String STEP_SUBFLOW = "subflow";

    // ==================== 失败策略 ====================

    /** 次数用尽后整条流程失败 */
    public static final String FAIL_FAST = "FAIL_FAST";

    /** 次数用尽后跳过本节点继续往下走 */
    public static final String FAIL_SKIP = "FAIL_SKIP";

    // ==================== 参数映射来源 ====================

    /** 启动流程时由调用方传入 */
    public static final String SOURCE_INPUT = "INPUT";

    /** 某个前置节点的输出 */
    public static final String SOURCE_NODE = "NODE";

    /** 编排时写死的值 */
    public static final String SOURCE_LITERAL = "LITERAL";

    // ==================== 上下文命名空间 ====================

    /** 启动入参 */
    public static final String NS_INPUT = "INPUT";

    /** 节点输出，按节点编码分桶 */
    public static final String NS_NODE = "NODE";

    /** 系统变量（executionNo、businessKey 等） */
    public static final String NS_SYSTEM = "SYSTEM";

    // ==================== 汇合默认值 ====================

    /** 缺省汇合策略 */
    public static final String DEFAULT_JOIN_POLICY = "ALL_SUCCESS";

    /** 缺省前置失败策略 */
    public static final String DEFAULT_JOIN_FAILURE_POLICY = "FAIL";

    // ==================== 等待与重试上下限 ====================

    /** 没填超时时使用。和设备回调的默认等待一致 */
    public static final long DEFAULT_TIMEOUT_MS = 300_000L;

    /** 最短等待。短于 1 秒分不清是配置错误还是真超时 */
    public static final long MIN_TIMEOUT_MS = 1_000L;

    /** 最长等待 24 小时。再长应拆成定时步骤，而不是把一条命令挂住 */
    public static final long MAX_TIMEOUT_MS = 86_400_000L;

    /** 含首次。默认 1，避免无意中放大物理动作次数 */
    public static final int DEFAULT_MAX_ATTEMPTS = 1;

    /** 重发是物理动作，把上限收在 5 次 */
    public static final int MAX_ATTEMPTS_LIMIT = 5;

    /** 循环上限。结构化容器有 maxIterations 兜底，但别让它过大 */
    public static final int MAX_ITERATIONS_LIMIT = 10_000;

    /** 一条流程的节点数上限。放开依赖编辑后靠它拦住误操作 */
    public static final int MAX_NODES = 60;

    /** 子流程嵌套深度上限（A 调 B、B 调 C…）。防引用链过长 */
    public static final int MAX_SUBFLOW_DEPTH = 5;

    /** FANOUT 一次最多扇出的子执行数 */
    public static final int MAX_FANOUT_GROUPS = 50;

    /** 步骤编码和节点编码：小写开头，方便出现在日志里 */
    public static final String NODE_ID_REGEX = "^[a-z][a-z0-9_]{0,31}$";

    /** 分支出口名：小写字母数字下划线，如 pass / fail / high */
    public static final String BRANCH_KEY_REGEX = "^[a-z][a-z0-9_]{0,31}$";

    /** 路由未命中时的兜底出口名。发布期强制要求存在 */
    public static final String BRANCH_DEFAULT = "default";

    // ==================== 运行层固定值 ====================

    /** 运行业务号前缀 */
    public static final String EXECUTION_NO_PREFIX = "exe";

    /** 命令来源：流程引擎 */
    public static final String SOURCE_TYPE_FLOW = "FLOW";

    /** 命令来源：人工测试 */
    public static final String SOURCE_TYPE_MANUAL = "MANUAL";

    /**
     * 状态的中文，用在「不能修改」的提示里。
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
