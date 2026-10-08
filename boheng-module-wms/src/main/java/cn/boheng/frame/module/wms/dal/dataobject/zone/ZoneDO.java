package cn.boheng.frame.module.wms.dal.dataobject.zone;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.math.BigDecimal;

/**
 * 区域树 DO
 *
 * 对应表 wms_zone_info，继承 TenantBaseDO，由租户拦截器自动隔离
 *
 * @author yinan
 */
@TableName("wms_zone_info")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoneDO extends TenantBaseDO {

    /**
     * 主键（雪花算法，应用侧生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 区域编码
     */
    private String zoneCode;

    /**
     * 父区域编码（为空表示根节点）
     */
    private String parentZoneCode;

    /**
     * 区域名称
     */
    private String zoneName;

    /**
     * 区域类型：LAB 实验室/ROOM 房间/FUNCTION 功能区/TEMP 温控区/SAFETY 安全区/RACK 料架/BENCH 台面/DEVICE 设备工位/CUSTOM 自定义
     */
    private String zoneType;

    /**
     * 树深度（根为 1）
     */
    private Integer zoneLevel;

    /**
     * 温区下限（℃）
     */
    private BigDecimal tempMin;

    /**
     * 温区上限（℃）
     */
    private BigDecimal tempMax;

    /**
     * 生物安全等级（1-4）
     */
    private Integer biosafetyLevel;

    /**
     * 扩展数据（JSON，如 rack 模板参数与物理地址）
     */
    private String extData;

    /**
     * 显示顺序
     */
    private Integer sortNo;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 备注
     */
    private String description;

}
