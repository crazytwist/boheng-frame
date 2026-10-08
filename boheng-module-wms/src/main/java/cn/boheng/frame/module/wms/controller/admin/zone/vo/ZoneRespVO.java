package cn.boheng.frame.module.wms.controller.admin.zone.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

/**
 * 区域树 Response VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 区域树 Response VO")
@Data
public class ZoneRespVO {

    @Schema(description = "主键", example = "1024")
    private Long id;

    @Schema(description = "区域编码", example = "ZONE_LAB_01")
    private String zoneCode;

    @Schema(description = "父区域编码（为空表示根节点）", example = "ZONE_LAB")
    private String parentZoneCode;

    @Schema(description = "区域名称", example = "一楼实验室")
    private String zoneName;

    @Schema(description = "区域类型：LAB 实验室/ROOM 房间/FUNCTION 功能区/TEMP 温控区/SAFETY 安全区/RACK 料架/BENCH 台面/DEVICE 设备工位/CUSTOM 自定义", example = "RACK")
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
    private String extData;

    @Schema(description = "显示顺序", example = "0")
    private Integer sortNo;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "备注", example = "冷藏区")
    private String description;

    @Schema(description = "子区域")
    private List<ZoneRespVO> children;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
