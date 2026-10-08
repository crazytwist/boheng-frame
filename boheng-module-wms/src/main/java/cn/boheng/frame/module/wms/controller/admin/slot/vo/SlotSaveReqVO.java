package cn.boheng.frame.module.wms.controller.admin.slot.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import cn.boheng.frame.framework.common.enums.CommonStatusEnum;
import cn.boheng.frame.framework.common.validation.InEnum;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 槽位 创建/修改 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 槽位 创建/修改 Request VO")
@Data
public class SlotSaveReqVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "槽位编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "SLOT_RACK_A01_R1C1")
    @NotEmpty(message = "槽位编码不能为空")
    @Size(max = 64, message = "槽位编码长度不能超过 64 个字符")
    private String slotCode;

    @Schema(description = "挂载区域编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "RACK_A01")
    @NotEmpty(message = "挂载区域编码不能为空")
    @Size(max = 64, message = "挂载区域编码长度不能超过 64 个字符")
    private String zoneCode;

    @Schema(description = "槽位类型：STORAGE 存储/DEVICE 设备器位/BUFFER 暂存/WASTE 废弃", requiredMode = Schema.RequiredMode.REQUIRED, example = "STORAGE")
    @NotEmpty(message = "槽位类型：STORAGE 存储/DEVICE 设备器位/BUFFER 暂存/WASTE 废弃不能为空")
    @Size(max = 32, message = "槽位类型：STORAGE 存储/DEVICE 设备器位/BUFFER 暂存/WASTE 废弃长度不能超过 32 个字符")
    private String slotType;

    @Schema(description = "可用状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "可用状态不能为空")
    @InEnum(value = CommonStatusEnum.class, message = "修改状态必须是 {value}")
    private Integer status;

    @Schema(description = "容量（标准位）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "容量（标准位）不能为空")
    private Integer capacity;

    @Schema(description = "是否一位一批", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    @NotNull(message = "是否一位一批不能为空")
    private Boolean uniqueBatch;

    @Schema(description = "扩展数据（JSON，坐标描述 row/col/layer）", example = "JSON 对象，如 rowNo/colNo/layerNo")
    @Size(max = 512, message = "扩展数据（JSON，坐标描述 row/col/layer）长度不能超过 512 个字符")
    private String extData;

    @Schema(description = "所属设备编码", example = "TECAN_F200_01")
    @Size(max = 64, message = "所属设备编码长度不能超过 64 个字符")
    private String deviceCode;

    @Schema(description = "设备侧位置标识", example = "STACK-3")
    @Size(max = 64, message = "设备侧位置标识长度不能超过 64 个字符")
    private String devicePositionNo;

    @Schema(description = "备注", example = "样本位")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String description;
}
