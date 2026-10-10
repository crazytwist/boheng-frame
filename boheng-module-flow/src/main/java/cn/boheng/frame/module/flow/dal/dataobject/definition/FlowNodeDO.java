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
 * 流程版本和步骤的一条关联。设备和动作在步骤表上，这里只记顺序和这一次怎么引用。
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
     * 关联的步骤
     */
    private Long stepId;

    /**
     * 这个版本里的节点编码。默认同步骤编码
     */
    private String nodeId;

    /**
     * 直线顺序，从 0 开始
     */
    private Integer sortNo;

    /**
     * 前置节点编码 JSON 数组。第一步是空数组
     */
    private String dependsOn;

    /**
     * 这一次引用的参数映射 JSON。没有映射时为空
     */
    private String inputMapping;

}
