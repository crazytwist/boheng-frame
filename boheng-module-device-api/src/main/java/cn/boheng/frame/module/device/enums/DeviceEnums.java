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

        ONLINE(0, "在线"),
        OFFLINE(1, "离线"),
        MAINTENANCE(2, "维护中"),
        FAULT(3, "故障");

        private final Integer status;
        private final String name;

    }

    /**
     * 接入方式（平台只认 HTTP + MQTT，其余协议经 Node-RED 收敛）
     */
    @Getter
    @AllArgsConstructor
    public enum ConnectionTypeEnum {

        HTTP("HTTP", "HTTP 直连"),
        MQTT("MQTT", "MQTT 直连"),
        NODE_RED("NODE_RED", "经 Node-RED 网关"),
        SIMULATED("SIMULATED", "仿真（假驱动）");

        private final String type;
        private final String name;

    }

    /**
     * 设备占用锁类型（对齐 SiLA Lock Controller）
     */
    @Getter
    @AllArgsConstructor
    public enum LockTypeEnum {

        COMMAND("COMMAND", "命令占用"),
        MANUAL("MANUAL", "人工锁定"),
        MAINTENANCE("MAINTENANCE", "维护锁定");

        private final String type;
        private final String name;

    }

    /**
     * 能力来源（能力自描述机制：驱动上报 vs 人工填写）
     */
    @Getter
    @AllArgsConstructor
    public enum CapabilitySourceEnum {

        DEVICE_DECLARED("DEVICE_DECLARED", "驱动上报"),
        MANUAL("MANUAL", "人工填写");

        private final String source;
        private final String name;

    }

    /**
     * 命令状态机（三态调用共用）
     */
    @Getter
    @AllArgsConstructor
    public enum CommandStatusEnum {

        PENDING("PENDING", "待下发"),
        QUEUED("QUEUED", "排队中"),
        DISPATCHED("DISPATCHED", "已下发"),
        RUNNING("RUNNING", "执行中"),
        SUCCESS("SUCCESS", "成功"),
        FAILED("FAILED", "失败"),
        TIMEOUT("TIMEOUT", "超时"),
        CANCELLED("CANCELLED", "已取消");

        private final String status;
        private final String name;

    }

    /**
     * 三态调用：下发方式（调用什么时候返回）
     */
    @Getter
    @AllArgsConstructor
    public enum DispatchModeEnum {

        SYNC("SYNC", "同步下发"),
        ASYNC("ASYNC", "异步下发");

        private final String mode;
        private final String name;

    }

    /**
     * 三态调用：回收方式（结果怎么回来）
     */
    @Getter
    @AllArgsConstructor
    public enum ResultModeEnum {

        SYNC_RETURN("SYNC_RETURN", "同步返回"),
        CALLBACK("CALLBACK", "异步回调"),
        POLL("POLL", "异步轮询");

        private final String mode;
        private final String name;

    }

}
