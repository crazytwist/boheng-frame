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

import java.time.LocalDateTime;

/**
 * 一条实验流程的一个版本。已发布之后这一行和它的节点都不再改。
 */
@TableName("flow_definition")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowDefinitionDO extends TenantBaseDO {

    /**
     * 主键，雪花算法
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 流程编码。创建后不可改
     */
    private String pipelineKey;

    /**
     * 版本号，从 1 递增
     */
    private Integer version;

    /**
     * 流程名称
     */
    private String name;

    /**
     * 说明
     */
    private String remark;

    /**
     * 入参声明 JSON。声明这条流程启动时要传的参数（键值对，值即默认值），
     * 启动时未显式传参则以声明兜底，参数映射的 INPUT 来源也按它给出字段候选。
     * 参数注释存在保留键 __comments__ 里，与步骤 default_params 同一约定。
     */
    private String inputParams;

    /**
     * DRAFT 草稿，PUBLISHED 已发布，RETIRED 已停用
     */
    private String status;

    /**
     * 发布时节点图的 SHA-256。草稿为空
     */
    private String checksum;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

}
