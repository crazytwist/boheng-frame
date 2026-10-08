package cn.boheng.frame.module.wms.dal.dataobject.instance;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.math.BigDecimal;

/**
 * 物料实例 DO
 *
 * 对应表 wms_material_instance，继承 TenantBaseDO，由租户拦截器自动隔离
 *
 * @author yinan
 */
@TableName("wms_material_instance")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialInstanceDO extends TenantBaseDO {

    /**
     * 主键（雪花算法，应用侧生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 实例编码
     */
    private String instanceCode;

    /**
     * 实例名称
     */
    private String instanceName;

    /**
     * 条码
     */
    private String barcode;

    /**
     * 容器类型编号
     */
    private Long containerTypeId;

    /**
     * 容器类型编码（冗余）
     */
    private String containerTypeCode;

    /**
     * 父实例编号（为空表示顶层）
     */
    private Long parentInstanceId;

    /**
     * 在父容器中的位置
     */
    private String parentPositionCode;

    /**
     * 物化路径
     */
    private String instancePath;

    /**
     * 根实例编号
     */
    private Long rootInstanceId;

    /**
     * 落位槽位编号（仅顶层实例）
     */
    private Long rootSlotId;

    /**
     * 落位槽位编码（冗余）
     */
    private String rootSlotCode;

    /**
     * 内容物定义编号（CARRIER 与空容器为空）
     */
    private Long contentDefId;

    /**
     * 内容物编码（冗余）
     */
    private String contentDefCode;

    /**
     * 内容物类型快照（CARRIER 为空；空容器为 EMPTY）
     */
    private String contentType;

    /**
     * 当前体积（μL）
     */
    private BigDecimal currentVolUl;

    /**
     * 当前数量（个）
     */
    private Integer currentCount;

    /**
     * 扩展数据（JSON，实例级覆盖描述）
     */
    private String extData;

    /**
     * 实例状态：AVAILABLE 可用/RESERVED 已预留/IN_USE 使用中/USED 已用完/EXPIRED 已过期/DISCARDED 已废弃
     */
    private String instanceStatus;

    /**
     * 备注
     */
    private String description;

}
