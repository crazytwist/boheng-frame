package cn.boheng.frame.module.device.dal.dataobject.action;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 设备动作定义 DO
 *
 * 对应表 device_action，继承 TenantBaseDO，由租户拦截器自动隔离。
 *
 * 「动作」= 这台设备能被怎么启动（READ_PLATE / OPEN_DOOR / STOP ...），
 * param_schema 描述参数模式，是「参数下发」的核心（前端按它动态渲染表单）。
 *
 * ⚠️ 表里有生成列 action_code_key（= device_type_code#action_code，唯一键用），此处不映射。
 *
 * @author yinan
 */
@TableName("device_action")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceActionDO extends TenantBaseDO {

    /**
     * 主键（雪花算法，应用侧生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 设备类型编码（动作归属的设备类型；行为建模，如 PLATE_READER）
     */
    private String deviceTypeCode;

    /**
     * 动作编码（如 READ_PLATE / OPEN_DOOR / STOP）
     */
    private String actionCode;

    /**
     * 动作名称（如 读板 / 开门 / 急停）
     */
    private String actionName;

    /**
     * 映射 SiLA 标准特性名（如 com.sila_standard.run_control；私有动作留空）
     */
    private String standardFeature;

    /**
     * 参数模式 JSON：
     * {参数名:{label,type,unit,required,default,constraints,source}}
     * 含默认值 + 约束 + 单位，前端按 schema 动态渲染表单
     */
    private String paramSchema;

    /**
     * 厂商映射（i-control 脚本名 / 方法名 / 协议命令）
     */
    private String vendorRef;

    /**
     * 能力来源: DEVICE_DECLARED 驱动上报 / MANUAL 手工（可覆盖）
     */
    private String source;

    /**
     * 预估耗时（毫秒）；< 3000 才允许 dispatch_mode=SYNC，长命令同步必超时
     */
    private Long estimateDurationMs;

    /**
     * 本动作下发前是否需先确认设备空闲。最终是否真预检还要看设备台账的 busy_check_policy。
     */
    private Boolean needBusyCheck;

    /**
     * 忙闲查询动作编码。need_busy_check 开启时指向同类型的另一个动作；为空则降级为不预检。
     */
    private String statusCommandCode;

    /**
     * 请求报文模板，支持 ${param} 占位符。
     */
    private String requestTemplate;

    /**
     * 轮询完成判定表达式，例如 $.status == "DONE"。
     */
    private String pollDoneExpr;

    /**
     * 最大轮询次数，超限判超时；为空走全局默认。
     */
    private Integer pollMaxTimes;

    /**
     * 启用状态: 0 启用 / 1 停用
     */
    private Integer status;

}
