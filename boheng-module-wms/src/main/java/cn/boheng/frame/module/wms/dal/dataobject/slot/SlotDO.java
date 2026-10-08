package cn.boheng.frame.module.wms.dal.dataobject.slot;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 槽位 DO
 *
 * 对应表 wms_slot_info，继承 TenantBaseDO，由租户拦截器自动隔离
 *
 * @author yinan
 */
@TableName("wms_slot_info")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotDO extends TenantBaseDO {

    /**
     * 主键（雪花算法，应用侧生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 槽位编码
     */
    private String slotCode;

    /**
     * 挂载区域编码
     */
    private String zoneCode;

    /**
     * 槽位类型：STORAGE 存储/DEVICE 设备器位/BUFFER 暂存/WASTE 废弃
     */
    private String slotType;

    /**
     * 可用状态
     */
    private Integer status;

    /**
     * 使用状态：FREE 空闲/OCCUPIED 占用/LOCKED 锁定/CHECKING 盘点中
     */
    private String slotStatus;

    /**
     * 容量（标准位）
     */
    private Integer capacity;

    /**
     * 是否一位一批
     */
    private Boolean uniqueBatch;

    /**
     * 扩展数据（JSON，坐标描述 row/col/layer）
     */
    private String extData;

    /**
     * 所属设备编码
     */
    private String deviceCode;

    /**
     * 设备侧位置标识
     */
    private String devicePositionNo;

    /**
     * 已占标准位数（=0 即 FREE）
     */
    private Integer occupiedQty;

    /**
     * 转为占用态时间
     */
    private LocalDateTime occupiedTime;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 备注
     */
    private String description;

}
