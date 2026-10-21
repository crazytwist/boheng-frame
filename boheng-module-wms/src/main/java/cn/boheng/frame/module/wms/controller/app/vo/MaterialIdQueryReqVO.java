package cn.boheng.frame.module.wms.controller.app.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "根据物料 ID 查询物料信息")
public class MaterialIdQueryReqVO {

    @NotEmpty(message = "物料ID不能为空")
    @Schema(description = "物料实例主键列表", name = "LabwareIdList")
    @JsonProperty("LabwareIdList")
    private List<Long> labwareIdList;
}
