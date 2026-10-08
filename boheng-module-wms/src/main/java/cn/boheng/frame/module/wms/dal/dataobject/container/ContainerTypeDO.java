package cn.boheng.frame.module.wms.dal.dataobject.container;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.math.BigDecimal;

/**
 * 容器类型 DO
 *
 * 对应表 wms_container_type，继承 TenantBaseDO，由租户拦截器自动隔离
 *
 * @author yinan
 */
@TableName("wms_container_type")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerTypeDO extends TenantBaseDO {

    /**
     * 主键（雪花算法，应用侧生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 容器类型编码
     */
    private String typeCode;

    /**
     * 容器类型名称
     */
    private String typeName;

    /**
     * 图片地址（可空，前端展示默认图）
     */
    private String imageUrl;

    /**
     * 物理形态：PLATE 孔板/TUBE 试管/BOTTLE 瓶/RACK 托盘架/VIAL 小瓶/TIP 吸头/CHIP 芯片/FILTER 滤膜/BOX 盒/BAG 袋/OTHER 其他
     */
    private String category;

    /**
     * 层级角色：CARRIER 载体/CONTAINER 直接容器/WELL 孔位
     */
    private String hierarchyRole;

    /**
     * 最大容积（μL）
     */
    private BigDecimal maxVolUl;

    /**
     * 位数（仅 CARRIER）
     */
    private Integer wellCount;

    /**
     * 行数（仅 CARRIER）
     */
    private Integer wellRows;

    /**
     * 列数（仅 CARRIER）
     */
    private Integer wellCols;

    /**
     * 位置命名规则：ROW_COL/SEQ/ROW_COL_LAYER/NONE
     */
    private String positionNaming;

    /**
     * 承载的子单元类型编码（仅 CARRIER）
     */
    private String childTypeCode;

    /**
     * 其他规格参数（JSON）
     */
    private String specJson;

    /**
     * 是否允许被装入其他容器
     */
    private Boolean nestable;

    /**
     * 使用类型：REUSABLE 周转复用/DISPOSABLE 一次性
     */
    private String usageType;

    /**
     * 复用次数上限（为空不限）
     */
    private Integer lifeCycles;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 备注
     */
    private String description;

}
