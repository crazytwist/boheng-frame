package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - 物料实例预留结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialInstanceReserveRespVO {

    private String reservationId;
    private List<Long> instanceIds;
    private List<Long> movementIds;
    private String operationId;
    private Boolean idempotentReplay;
}
