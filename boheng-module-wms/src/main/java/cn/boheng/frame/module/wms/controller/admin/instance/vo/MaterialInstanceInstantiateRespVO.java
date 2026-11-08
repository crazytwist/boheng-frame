package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "管理后台 - 样本实物化结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialInstanceInstantiateRespVO {

    private String sampleId;
    private List<Long> instanceIds;
    private String operationId;
    private Boolean idempotentReplay;
}
