package cn.boheng.frame.module.flow.service.definition.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 校验和保存时用的一步。顺序就是列表顺序，不信任调用方传来的依赖数组。
 */
@Data
public class FlowNodeDraft {

    /**
     * 版本内步骤编码
     */
    private String nodeId;

    /**
     * 步骤名称
     */
    private String name;

    /**
     * 步骤类型。当前只能是 device.command
     */
    private String stepType;

    /**
     * 台账上的设备编码
     */
    private String deviceCode;

    /**
     * 该设备类型下的动作编码
     */
    private String actionCode;

    /**
     * 最多等待的毫秒数
     */
    private Long timeoutMs;

    /**
     * 含首次的尝试次数
     */
    private Integer maxAttempts;

    /**
     * 失败策略。当前只能是 FAIL_FAST
     */
    private String failStrategy;

    /**
     * 参数映射。可以为空，表示这一步只按动作自己的默认参数下发
     */
    private List<FlowMapping> mappings = new ArrayList<>();

}
