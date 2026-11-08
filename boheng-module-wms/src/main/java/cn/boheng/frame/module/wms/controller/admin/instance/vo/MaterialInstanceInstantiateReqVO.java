package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "管理后台 - 样本实物化 Request VO")
@Data
public class MaterialInstanceInstantiateReqVO {

    @NotEmpty(message = "样本编号不能为空")
    private String sampleId;

    @NotEmpty(message = "容器类型编码不能为空")
    private String containerTypeCode;

    @NotNull(message = "数量不能为空")
    @Positive(message = "数量必须大于 0")
    private Integer quantity;

    private BigDecimal initialVolUl;

    private String idempotentKey;
}
