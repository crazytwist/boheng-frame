package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

/**
 * device.task 设备异步任务的类型配置。
 * 节点执行时把任务投递到队列（不直接调设备），机器端拉取执行后回报完成。
 */
@Data
public class DeviceTaskConfig {

    /**
     * 目标设备编码。空 = 不限设备，机器端可按 actionCode 跨设备拉取
     */
    private String deviceCode;

    /**
     * 任务类型 / 动作编码（设备台账上的动作）。机器端可按它拉取
     */
    private String actionCode;

    /**
     * 任务类型对应的参数集主键。参数集提供固定默认值，流程节点映射可覆盖。
     */
    private Long paramSetId;

}
