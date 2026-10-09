package cn.boheng.frame.module.device.dal.dataobject.analysis;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
/**
 * 分析结论。一次测量可以有多条
 */

@TableName("device_analysis_result")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceAnalysisResultDO extends TenantBaseDO {
    /**
     * 主键，雪花算法
     */

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联测量编号
     */
    private Long measurementId;

    /**
     * 设备编号
     */
    private Long deviceId;

    /**
     * 结论类型
     */
    private String resultType;

    /**
     * 结论值
     */
    private String resultValue;

    /**
     * 结论来源。解析写入的记为 INTERNAL
     */
    private String sourceSystem;

    /**
     * 解析后的完整对象
     */
    private String summaryJson;

    /**
     * 备注
     */
    private String remark;

}
