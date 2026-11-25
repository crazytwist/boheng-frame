package cn.boheng.frame.module.device.dal.dataobject.action;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
     * HTTP 方法。与台账上的接入地址拼接时使用。
     */
    private String httpMethod;

    /**
     * 请求报文格式: JSON / FORM / TEXT / XML。
     */
    private String bodyFormat;

    /**
     * 接口相对路径，如 /api/read。主机在设备台账，动作按设备类型共用，不写完整地址。
     */
    private String requestPath;

    /** 平台轮询查询接口相对路径；为空时兼容使用 requestPath。 */
    private String pollPath;

    /**
     * 请求报文模板，支持 ${param} 占位符。
     */
    private String requestTemplate;

    /**
     * 响应解析规则编码。调用成功后用它把响应写成测量。
     */
    private String codecCode;

    /**
     * 轮询完成判定表达式，例如 $.status == "DONE"。
     */
    private String pollDoneExpr;

    /**
     * 响应判定规则 JSON：成功判定字段路径与取值、失败说明字段、可重试性字段。
     *
     * <p>{successStatusPath, successStatusValues, successCodePath, successCodeValues,
     * messagePath, retryHintsPath}；为空时按 status/success/ExecuteStatus 兼容判定。</p>
     */
    private String resultRule;

    /**
     * 固定地址回调时的关联键路径，例如 {@code $.reqCode}。
     *
     * <p>有些设备（如海康 RCS）的回调地址是厂商侧写死的，报文里既不给我们命令号，
     * 也没法把带令牌的回调地址传过去。这种设备只能靠「我们发给它、它原样带回来」的
     * 某个业务字段认命令。这里配的就是从请求报文里取哪个字段当关联键，
     * 渲染后写进 device_command.correlate_value，回调时用它反查命令。</p>
     *
     * <p>可以写多个、逗号分隔，按顺序取第一个有值的（如 {@code $.EventId,$.RequestId}
     * —— 关联键列只存得下一个，另一个靠回调时的报文兜底匹配认）。</p>
     *
     * <p>为空表示该动作只支持带命令号的回调地址（{@code /api/callback/{commandNo}}）。</p>
     */
    private String correlatePath;

    /** 平台轮询的最大等待时间（秒），系统按设备轮询间隔换算查询次数。 */
    private Integer pollTimeoutSec;

    /**
     * 最大轮询次数，超限判超时；为空走全局默认。
     */
    private Integer pollMaxTimes;

    /**
     * 结果怎么拿: SYNC_RETURN 直接看响应 / CALLBACK 等设备回调
     */
    private String resultMode;

    /**
     * 等回调的秒数。为空时用设备台账上的默认值，清空也要写回库。
     *
     * <p>0 表示不超时：{@code device_command.timeout_at} 留空，超时扫描器不会收口，
     * 由上层流程节点的 {@code timeout_ms} 决定等多久。</p>
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer callbackTimeoutSec;

    /**
     * 启用状态: 0 启用 / 1 停用
     */
    private Integer status;

}
