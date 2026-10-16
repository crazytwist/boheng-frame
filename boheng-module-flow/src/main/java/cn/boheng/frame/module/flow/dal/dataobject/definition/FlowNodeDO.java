package cn.boheng.frame.module.flow.dal.dataobject.definition;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 流程版本里的一个节点。普通节点引用能力目录（stepId 指向 flow_step）；
 * route / loop / subflow / timer.wait 是 Flow 内建的控制类节点，stepId 为空，
 * 配置全在 typeConfig。编排形态（依赖、汇合策略、分支出口）都落在这一行。
 */
@TableName("flow_node")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowNodeDO extends TenantBaseDO {

    /**
     * 主键，雪花算法
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 所属流程版本
     */
    private Long definitionId;

    /**
     * 引用的能力（flow_step.id）。控制类节点为空
     */
    private Long stepId;

    /**
     * 这个版本里的节点编码。引用能力时默认同步骤编码
     */
    private String nodeId;

    /**
     * 节点显示名。控制类节点必填；引用能力时为空，展示时取步骤名称
     */
    private String name;

    /**
     * 直线顺序，从 0 开始。画布编排后只作展示排序，真相源是 dependsOn
     */
    private Integer sortNo;

    /**
     * 前置节点编码 JSON 数组。多前置 = 汇合；被多个节点依赖 = 扇出并行
     */
    private String dependsOn;

    /**
     * 这一次引用的参数映射 JSON。来源 INPUT / NODE / LITERAL
     */
    private String inputMapping;

    /**
     * 节点级类型覆盖。空则取 flow_step.step_type；控制类节点必填
     */
    private String stepType;

    /**
     * 节点级类型配置 JSON。引用能力时为空（用 flow_step.type_config），控制类节点必填
     */
    private String typeConfig;

    /**
     * 节点输出归一化到上下文的映射 JSON：{输出字段: 上下文键}
     */
    private String outputMapping;

    /**
     * 节点级运行覆盖 JSON：{"timeoutMs":..,"maxAttempts":..,"failureStrategy":"FAIL_FAST"}
     */
    private String runConfig;

    /**
     * 汇合策略：ALL_SUCCESS 全部成功（默认）/ ANY_SUCCESS 任一成功 / ALL_DONE 全部终态
     */
    private String joinPolicy;

    /**
     * 前置失败策略：FAIL 跟着失败（默认）/ SKIP 裁剪本节点
     */
    private String joinFailurePolicy;

    /**
     * 控制类节点的出口映射 JSON：{"pass":["nodeId"],"default":["nodeId"]}
     */
    private String branches;

    /**
     * 画布横坐标（拖拽编排）。空值由前端首次布局回填
     */
    private Integer x;

    /**
     * 画布纵坐标（拖拽编排）。空值由前端首次布局回填
     */
    private Integer y;

}
