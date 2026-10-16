package cn.boheng.frame.module.flow.controller.admin.relation.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 拖拽画布批量保存节点位置。只更新坐标，不动依赖与配置，
 * 因此不触发结构校验（位置不改变图结构），拖完节点即时落库。
 */
@Schema(description = "管理后台 - 流程节点布局 Request VO")
@Data
public class FlowRelationLayoutReqVO {

    @Schema(description = "要保存坐标的节点", requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    @NotNull(message = "布局不能为空")
    private List<Item> items;

    @Schema(description = "单个节点坐标")
    @Data
    public static class Item {

        @Schema(description = "关联编号", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "关联编号不能为空")
        private Long id;

        @Schema(description = "画布横坐标", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "横坐标不能为空")
        private Integer x;

        @Schema(description = "画布纵坐标", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "纵坐标不能为空")
        private Integer y;

    }

}
