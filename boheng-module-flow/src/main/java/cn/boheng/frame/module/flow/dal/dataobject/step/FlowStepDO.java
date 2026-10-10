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
 * 可复用的实验步骤。一条步骤是一次设备动作，流程通过关联表引用它。
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
     * 说明
     */
    private String remark;

    /**
     * 0 启用，可以被流程引用；1 停用
     */
    private Integer status;

}
