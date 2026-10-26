package cn.boheng.frame.module.device.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 设备域 通用枚举
 *
 * 契约层（device-api）提供，供消费方（调度域等）在跨模块调用时引用，避免硬编码字符串。
 *
 * @author yinan
 */
public class DeviceEnums {

    /**
     * 设备在线状态
     */
    @Getter
    @AllArgsConstructor
    public enum DeviceStatusEnum {

        /** 在线。 */
        ONLINE(0, "在线"),
        /** 离线。 */
        OFFLINE(1, "离线"),
        /** 维护中。 */
        MAINTENANCE(2, "维护中"),
        /** 故障。 */
        FAULT(3, "故障");

        /** 状态编码。 */
        private final Integer status;
        /** 状态名称。 */
        private final String name;

    }

    /**
     * 接入方式（平台只认 HTTP + MQTT，其余协议经 Node-RED 收敛）
     */
    @Getter
    @AllArgsConstructor
    public enum ConnectionTypeEnum {

        /** HTTP 直连。 */
        HTTP("HTTP", "HTTP 直连"),
        /** MQTT 直连。 */
        MQTT("MQTT", "MQTT 直连"),
        /** 经 Node-RED 网关接入。 */
        NODE_RED("NODE_RED", "经 Node-RED 网关"),
        /** 仿真设备。 */
        SIMULATED("SIMULATED", "仿真（假驱动）");

        /** 接入类型编码。 */
        private final String type;
        /** 接入类型名称。 */
        private final String name;

    }

    /**
     * 任务接入模式：设备主动拉取，或平台主动接收并下发。
     */
    @Getter
    @AllArgsConstructor
    public enum TaskModeEnum {

        /** 设备主动拉取。 */
        PULL("PULL", "设备拉取任务"),
        /** 平台主动推送。 */
        PUSH("PUSH", "平台接收任务");

        /** 任务模式编码。 */
        private final String mode;
        /** 任务模式名称。 */
        private final String name;

    }

    /**
     * 设备占用锁类型（对齐 SiLA Lock Controller）
     */
    @Getter
    @AllArgsConstructor
    public enum LockTypeEnum {

        /** 命令占用。 */
        COMMAND("COMMAND", "命令占用"),
        /** 人工锁定。 */
        MANUAL("MANUAL", "人工锁定"),
        /** 维护锁定。 */
        MAINTENANCE("MAINTENANCE", "维护锁定");

        /** 锁类型编码。 */
        private final String type;
        /** 锁类型名称。 */
        private final String name;

    }

    /**
     * 能力来源（能力自描述机制：驱动上报 vs 人工填写）
     */
    @Getter
    @AllArgsConstructor
    public enum CapabilitySourceEnum {

        /** 设备驱动上报。 */
        DEVICE_DECLARED("DEVICE_DECLARED", "驱动上报"),
        /** 人工维护。 */
        MANUAL("MANUAL", "人工填写");

        /** 来源编码。 */
        private final String source;
        /** 来源名称。 */
        private final String name;

    }

    /**
     * 命令状态机（三态调用共用）
     */
    @Getter
    @AllArgsConstructor
    public enum CommandStatusEnum {

        /** 已取得执行权。 */
        ACQUIRED("ACQUIRED", "已取得执行权"),
        /** 已下发设备。 */
        SENT("SENT", "已下发"),
        /** 等待设备结果。 */
        WAITING("WAITING", "等待设备结果"),
        /** 执行成功。 */
        SUCCEEDED("SUCCEEDED", "成功"),
        /** 执行失败。 */
        FAILED("FAILED", "失败"),
        /** 执行超时。 */
        TIMED_OUT("TIMED_OUT", "超时"),
        /** 已取消。 */
        CANCELLED("CANCELLED", "已取消");

        /** 命令状态编码。 */
        private final String status;
        /** 命令状态名称。 */
        private final String name;

    }

    /**
     * 三态调用：下发方式（调用什么时候返回）
     */
    @Getter
    @AllArgsConstructor
    public enum DispatchModeEnum {

        /** 同步下发。 */
        SYNC("SYNC", "同步下发"),
        /** 异步下发。 */
        ASYNC("ASYNC", "异步下发");

        /** 下发模式编码。 */
        private final String mode;
        /** 下发模式名称。 */
        private final String name;

    }

    /**
     * 三态调用：回收方式（结果怎么回来）
     */
    @Getter
    @AllArgsConstructor
    public enum ResultModeEnum {

        /** 同步返回。 */
        SYNC_RETURN("SYNC_RETURN", "同步返回"),
        /** 设备回调。 */
        CALLBACK("CALLBACK", "异步回调"),
        /** 平台轮询。 */
        POLL("POLL", "异步轮询");

        /** 结果模式编码。 */
        private final String mode;
        /** 结果模式名称。 */
        private final String name;

    }

    /**
     * 设备命令暂停收口时等待的外部事实。
     */
    @Getter
    @AllArgsConstructor
    public enum CommandWaitingReasonEnum {

        /** 等待设备回调。 */
        DEVICE_CALLBACK("DEVICE_CALLBACK", "等待设备回调"),
        /** 等待下一次轮询。 */
        POLL_TICK("POLL_TICK", "等待下一次轮询");

        /** 等待原因编码。 */
        private final String reason;
        /** 等待原因名称。 */
        private final String name;

    }

    /**
     * 设备并发处理策略。当前调度系统只消费结果，具体策略由设备侧执行。
     */
    @Getter
    @AllArgsConstructor
    public enum ConcurrencyPolicyEnum {

        /** 设备独占执行。 */
        EXCLUSIVE("EXCLUSIVE", "设备独占"),
        /** 由设备侧排队。 */
        DEVICE_QUEUED("DEVICE_QUEUED", "设备侧排队"),
        /** 由平台侧排队。 */
        PLATFORM_QUEUED("PLATFORM_QUEUED", "平台侧排队");

        /** 并发策略编码。 */
        private final String policy;
        /** 并发策略名称。 */
        private final String name;

    }

}
