package cn.boheng.frame.module.flow.dal.dataobject.step;

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
 * 可复用的实验步骤（能力目录的一行）。一种能力 = step_type + type_config：
 * 设备动作的设备编码和动作码收在 type_config 里，新增类型零改表。
 * 流程通过 flow_node 引用它。
 */
@TableName("flow_step")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowStepDO extends TenantBaseDO {

    /**
     * 主键，雪花算法
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 步骤编码。创建后不可改
     */
    private String stepKey;

    /**
     * 步骤名称
     */
    private String name;

    /**
     * 步骤类型 key。见 FlowConstants.STEP_*，必须在 StepTypeRegistry 里注册过
     */
    private String stepType;

    /**
     * 类型专属配置 JSON。device.command 存 {"deviceCode":..,"actionCode":..}
     */
    private String typeConfig;

    /**
     * 通用默认输入 JSON。节点引用时可覆盖
     */
    private String defaultParams;

    /**
     * 输出声明 JSON：{输出字段: 说明}。
     * 声明这一步会往流程上下文里写哪些字段，是编排侧输出映射的契约；
     * 未声明（空）= 输出动态，不参与契约校验。
     */
    private String outputSchema;

    /**
     * 最多等待的毫秒数
     */
    private Long timeoutMs;

    /**
     * 含首次的尝试次数
     */
    private Integer maxAttempts;

    /**
     * 说明
     */
    private String remark;

    /**
     * 0 启用，可以被流程引用；1 停用
     */
    private Integer status;

}
