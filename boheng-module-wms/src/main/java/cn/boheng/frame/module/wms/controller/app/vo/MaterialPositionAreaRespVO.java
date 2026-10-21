package cn.boheng.frame.module.wms.controller.app.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "可用位置编码列表")
public class MaterialPositionAreaRespVO {

    @Schema(description = "槽位编码或载具内孔位编码", name = "Position")
    @JsonProperty("Position")
    private List<String> position;

    public static MaterialPositionAreaRespVO empty() {
        return new MaterialPositionAreaRespVO(Collections.emptyList());
    }
}
