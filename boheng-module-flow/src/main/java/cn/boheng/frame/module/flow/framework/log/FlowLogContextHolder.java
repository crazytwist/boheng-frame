package cn.boheng.frame.module.flow.framework.log;

/**
 * 流程执行日志上下文（ThreadLocal）。
 *
 * <p>和租户日志 {@code TenantLogConverter} 同源：日志想在「一条运行」上贯穿，
 * 就得把 executionNo / nodeId 挂到线程上，再由 logback 转换器 {@link FlowLogConverter}
 * 输出到每行日志前缀。引擎在推进时写入，运行收尾时清理。</p>
 *
 * <p>注意：设备步骤在工作线程池里执行，入口处要显式写入再清理，
 * 避免跨线程串味。</p>
 *
 * @author yinan
 */
public final class FlowLogContextHolder {

    /**
     * 运行编号（exe_xxx）
     */
    private static final ThreadLocal<String> EXECUTION_NO = new ThreadLocal<>();

    /**
     * 节点编码（当前正在执行的节点，可空）
     */
    private static final ThreadLocal<String> NODE_ID = new ThreadLocal<>();

    private FlowLogContextHolder() {
    }

    public static void setExecutionNo(String executionNo) {
        if (executionNo == null) {
            EXECUTION_NO.remove();
        } else {
            EXECUTION_NO.set(executionNo);
        }
    }

    public static void setNodeId(String nodeId) {
        if (nodeId == null) {
            NODE_ID.remove();
        } else {
            NODE_ID.set(nodeId);
        }
    }

    public static String getExecutionNo() {
        return EXECUTION_NO.get();
    }

    public static String getNodeId() {
        return NODE_ID.get();
    }

    /**
     * 清空本线程的流程日志上下文（运行收尾时调用）
     */
    public static void clear() {
        EXECUTION_NO.remove();
        NODE_ID.remove();
    }

}
