package cn.boheng.frame.module.device.dal.dataobject.simulate;

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
 * 仿真 HTTP 配置。
 *
 * 对应表 device_simulate_profile。一台设备的一个动作一条，描述这次 HTTP 怎么发、怎么收。
 */
@TableName("device_simulate_profile")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceSimulateProfileDO extends TenantBaseDO {

    /**
     * 主键，雪花算法
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 设备编号
     */
    private Long deviceId;

    /**
     * 设备编码。仿真 HTTP 按编码找到这台设备的配置
     */
    private String deviceCode;

    /**
     * 动作编码
     */
    private String actionCode;

    /**
     * 回收方式：SYNC 同步 / CALLBACK 设备回调 / POLL 轮询
     */
    private String returnMode;

    /**
     * 等待毫秒。CALLBACK 是受理后多久回调，POLL 是结果就绪前的时长
     */
    private Integer asyncWaitMs;

    /**
     * 两次轮询之间的等待毫秒
     */
    private Integer pollIntervalMs;

    /**
     * 最大轮询次数，超过则这次调用超时
     */
    private Integer pollMaxTimes;

    /**
     * 请求报文模板，支持 ${参数名}
     */
    private String requestTemplate;

    /**
     * 最终返回报文，支持 ${参数名} 和 ${jobId}
     */
    private String responseBody;

    /**
     * 轮询首次受理报文。空则使用默认受理 JSON
     */
    private String ackBody;

    /**
     * 轮询尚未完成时返回的报文。空则使用默认进行中 JSON
     */
    private String pendingBody;

    /**
     * 启用状态：0 启用 / 1 停用
     */
    private Integer status;

}
