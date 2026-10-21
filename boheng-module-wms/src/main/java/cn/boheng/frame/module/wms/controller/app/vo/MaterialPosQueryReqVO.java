package cn.boheng.frame.module.wms.controller.app.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "根据位置查询实验室物料")
public class MaterialPosQueryReqVO {

    @NotEmpty(message = "位置列表不能为空")
    @Schema(description = "位置列表", name = "PosList")
    @JsonProperty("PosList")
    private List<String> posList;
}
