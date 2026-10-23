package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

/**
 * device.command 的类型配置。
 * v3 起设备编码和动作从 flow_step 独立列收敛到这里。
 */
@Data
public class DeviceCommandConfig {

    /**
     * 台账上的设备编码
     */
    private String deviceCode;

    /**
     * 该设备类型下的动作编码
     */
    private String actionCode;

    /**
     * 设备参数集主键。运行期读取其默认值，再由步骤默认参数和节点映射覆盖。
     */
    private Long paramSetId;

    /**
     * 解析规则编码。空则用动作上配置的规则
     */
    private String codecCode;

}
