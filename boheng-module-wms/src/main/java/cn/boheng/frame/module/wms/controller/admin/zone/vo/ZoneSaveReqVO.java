package cn.boheng.frame.module.wms.controller.admin.zone.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import cn.boheng.frame.framework.common.enums.CommonStatusEnum;
import cn.boheng.frame.framework.common.validation.InEnum;
import java.math.BigDecimal;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 区域树 创建/修改 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 区域树 创建/修改 Request VO")
@Data
public class ZoneSaveReqVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "区域编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "ZONE_LAB_01")
    @NotEmpty(message = "区域编码不能为空")
    @Size(max = 64, message = "区域编码长度不能超过 64 个字符")
    private String zoneCode;

    @Schema(description = "父区域编码（为空表示根节点）", example = "ZONE_LAB")
    @Size(max = 64, message = "父区域编码（为空表示根节点）长度不能超过 64 个字符")
    private String parentZoneCode;

    @Schema(description = "区域名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "一楼实验室")
    @NotEmpty(message = "区域名称不能为空")
    @Size(max = 128, message = "区域名称长度不能超过 128 个字符")
    private String zoneName;

    @Schema(description = "区域类型：LAB 实验室/ROOM 房间/FUNCTION 功能区/TEMP 温控区/SAFETY 安全区/RACK 料架/BENCH 台面/DEVICE 设备工位/CUSTOM 自定义", requiredMode = Schema.RequiredMode.REQUIRED, example = "RACK")
    @NotEmpty(message = "区域类型：LAB 实验室/ROOM 房间/FUNCTION 功能区/TEMP 温控区/SAFETY 安全区/RACK 料架/BENCH 台面/DEVICE 设备工位/CUSTOM 自定义不能为空")
    @Size(max = 32, message = "区域类型：LAB 实验室/ROOM 房间/FUNCTION 功能区/TEMP 温控区/SAFETY 安全区/RACK 料架/BENCH 台面/DEVICE 设备工位/CUSTOM 自定义长度不能超过 32 个字符")
    private String zoneType;

    @Schema(description = "树深度（根为 1）", example = "1")
    private Integer zoneLevel;

    @Schema(description = "温区下限（℃）", example = "2.00")
    private BigDecimal tempMin;

    @Schema(description = "温区上限（℃）", example = "8.00")
    private BigDecimal tempMax;

    @Schema(description = "生物安全等级（1-4）", example = "2")
    private Integer biosafetyLevel;

    @Schema(description = "扩展数据（JSON，如 rack 模板参数与物理地址）", example = "JSON 对象，如 rackRows/rackCols/slotCapacity/address")
    @Size(max = 1024, message = "扩展数据（JSON，如 rack 模板参数与物理地址）长度不能超过 1024 个字符")
    private String extData;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "显示顺序不能为空")
    private Integer sortNo;

    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    @InEnum(value = CommonStatusEnum.class, message = "修改状态必须是 {value}")
    private Integer status;

    @Schema(description = "备注", example = "冷藏区")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String description;
}
