package cn.boheng.frame.module.flow.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 流程运行期固定值的唯一定义入口。
 *
 * <p>枚举名与数据库保存值保持一致，DO/VO 继续使用 String，业务代码通过
 * {@link Enum#name()} 写入和比较，避免运行状态、等待原因和信号类型散落为裸字符串。</p>
 */
public final class FlowRuntimeEnums {

    private FlowRuntimeEnums() {
    }

    /** 流程实例状态。 */
    public enum ExecutionStatus {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED,
        CANCELLED;

        private static final Set<ExecutionStatus> TERMINAL = EnumSet.of(SUCCESS, FAILED, CANCELLED);

        /**
         * 判断是否为不可继续推进的终态。
         *
         * @return 是否为终态
         */
        public boolean isTerminal() {
            return TERMINAL.contains(this);
        }

        /**
         * 判断数据库状态值是否为流程实例终态。
         *
         * @param status 数据库存储值
         * @return 是否为终态
         */
        public static boolean isTerminal(String status) {
            if (status == null) {
                return false;
            }
            try {
                return valueOf(status).isTerminal();
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
    }

    /** 节点执行状态。 */
    public enum StepStatus {
        PENDING,
        RUNNING,
        WAITING,
        SUCCESS,
        FAILED,
        DEAD,
        SKIPPED,
        CANCELLED;

        private static final Set<StepStatus> TERMINAL = EnumSet.of(SUCCESS, FAILED, DEAD, SKIPPED, CANCELLED);

        /**
         * 判断数据库状态值是否为节点终态。
         *
         * @param status 数据库存储值
         * @return 是否为终态
         */
        public static boolean isTerminal(String status) {
            if (status == null) {
                return false;
            }
            try {
                return TERMINAL.contains(valueOf(status));
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
    }

    /** 节点暂停推进时等待的外部事实。 */
    public enum WaitingReason {
        MANUAL,
        TIMER,
        DEVICE_CALLBACK,
        DEVICE_COMMAND_CALLBACK,
        CHILD,
        EXTERNAL_CONFIRMATION
    }

    /** 写入流程信号收件箱的信号类型。 */
    public enum SignalType {
        MANUAL,
        TIMER,
        CHILD,
        EXTERNAL,
        RECOVERY
    }

    /** 设备拉取任务在流程域内的状态。 */
    public enum DeviceTaskStatus {
        PENDING,
        CLAIMED,
        DONE,
        FAILED,
        CANCELLED
    }

    /** 设备任务事件流水类型。 */
    public enum DeviceTaskEventType {
        CREATED,
        CLAIMED,
        DONE,
        FAILED
    }

    /** 流程实例的触发来源。 */
    public enum TriggerType {
        MANUAL,
        CRON,
        CHILD
    }

    /** 子流程的调度方式。 */
    public enum SubflowDispatchMode {
        SINGLE,
        FANOUT
    }

    /** 多前置节点的汇合策略。 */
    public enum JoinPolicy {
        ALL_SUCCESS,
        ANY_SUCCESS,
        ALL_DONE
    }

    /** 前置节点失败后的处理策略。 */
    public enum JoinFailurePolicy {
        FAIL,
        SKIP
    }

    /** 节点被跳过的原因。 */
    public enum SkipReason {
        BRANCH,
        UPSTREAM,
        FAILURE
    }

    /** 子流程汇合完成策略。 */
    public enum SubflowCompletionPolicy {
        ALL_SUCCESS,
        ANY_SUCCESS
    }
}
