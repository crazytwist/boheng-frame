package cn.boheng.frame.module.device.controller.admin.device.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 设备台账画像：按「怎么接入 / 命令怎么下 / 现在忙不忙 / 这类设备能做什么」组织，
 * 而不是把 device_info 的列原样返回给详情页。
 */
@Schema(description = "管理后台 - 设备台账画像")
@Data
public class DevicePortraitRespVO {

    @Schema(description = "台账本身")
    private DeviceRespVO device;

    @Schema(description = "接入方式的一句话说明")
    private String connectionSummary;

    @Schema(description = "忙闲校验与并发策略合成的下发说明")
    private String dispatchSummary;

    @Schema(description = "当前占用或在途的说明")
    private String occupancySummary;

    @Schema(description = "该设备类型可启动的动作")
    private List<Facet> actions;

    @Schema(description = "该设备类型可观测的属性")
    private List<Facet> properties;

    @Schema(description = "该设备类型可复用的参数集")
    private List<Facet> paramSets;

    @Schema(description = "能力条目")
    @Data
    public static class Facet {

        @Schema(description = "名称")
        private String title;

        @Schema(description = "编码")
        private String code;

        @Schema(description = "补充说明")
        private String detail;

        @Schema(description = "是否启用")
        private Boolean active;

    }

}
