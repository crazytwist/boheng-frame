package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - 物料实例 可用性检查结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialInstanceCheckRespVO {

    @Schema(description = "全部明细均可用")
    private Boolean ok;

    @Schema(description = "明细结果")
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long instanceId;
        private String instanceCode;
        private String instanceStatus;
        private Boolean available;
        private String reason;
    }

}
