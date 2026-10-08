package cn.boheng.frame.module.device.dal.dataobject.paramset;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 参数集预设 DO
 *
 * 对应表 device_param_set。「这一次下什么参数」的可复用模板：
 * params_json 与 device_action.param_schema 对齐，只有 validated 的参数集才能被用于发起命令。
 *
 * ⚠️ 表里有生成列 param_set_code_key（唯一键用），此处不映射。
 *
 * @author yinan
 */
@TableName("device_param_set")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceParamSetDO extends TenantBaseDO {

    /**
     * 主键（雪花算法，应用侧生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 参数集编码
     */
    private String paramSetCode;

    /**
     * 参数集名称
     */
    private String paramSetName;

    /**
     * 设备类型编码
     */
    private String deviceTypeCode;

    /**
     * 关联动作编码
     */
    private String actionCode;

    /**
     * 参数取值 JSON（与 device_action.param_schema 对齐）
     */
    private String paramsJson;

    /**
     * 是否已验证（只有 validated 才能被用于发起命令）
     */
    private Boolean validated;

    /**
     * 验证人
     */
    private String validatedBy;

    /**
     * 验证时间
     */
    private LocalDateTime validatedTime;

    /**
     * 启用状态: 0 启用 / 1 停用
     */
    private Integer status;

}
