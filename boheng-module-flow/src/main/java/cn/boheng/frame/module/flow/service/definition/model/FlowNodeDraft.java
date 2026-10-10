package cn.boheng.frame.module.flow.service.definition.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 校验和保存时用的一步。图形态字段（依赖、汇合、分支）从这里进
 * FlowGraphChecker 做发布校验；能力字段由步骤表或节点配置解析而来。
 */
@Data
public class FlowNodeDraft {

    /**
     * 版本内节点编码
     */
    private String nodeId;

    /**
     * 节点名称。引用能力时取步骤名称，控制节点单独填
     */
    private String name;

    /**
     * 生效的步骤类型 key
     */
    private String stepType;

    /**
     * 生效的类型配置 JSON。设备编码和动作码收在这里
     */
    private String typeConfig;

    /**
     * 前置节点编码。空 = 无前置（起点）
     */
    private List<String> dependsOn = new ArrayList<>();

    /**
     * 汇合策略：ALL_SUCCESS / ANY_SUCCESS / ALL_DONE
     */
    private String joinPolicy;

    /**
     * 前置失败策略：FAIL / SKIP
     */
    private String joinFailurePolicy;

    /**
     * 出口映射（route 专用）：出口名 -> 目标节点编码列表
     */
    private Map<String, List<String>> branches;

    /**
     * 参数映射
     */
    private List<FlowMapping> mappings = new ArrayList<>();

    /**
     * 循环体入口节点（loop 专用，图校验用）
     */
    private String loopBodyEntry;

    /**
     * 循环体出口节点（loop 专用，图校验用）
     */
    private String loopBodyExit;

    /**
     * 子流程目标编码（subflow 专用，图校验用）
     */
    private String subflowTargetKey;

    /**
     * 等待毫秒数（生效值）
     */
    private Long timeoutMs;

    /**
     * 尝试次数上限（生效值）
     */
    private Integer maxAttempts;

    /**
     * 失败策略：FAIL_FAST / FAIL_SKIP
     */
    private String failStrategy;

    /**
     * 关联的能力编号。控制类节点为空
     */
    private Long stepId;

}
