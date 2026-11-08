package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 综合可用性检查（样本/试剂/容器/槽位）。
 * 与按实例 {@code MaterialApi.check} 并列；不占用资源。
 */
@Data
public class MaterialAvailabilityReqDTO implements Serializable {

    /** 可选：调用方执行编号（仅追溯，不影响判定） */
    private String executionId;

    private List<MaterialAvailabilityItemDTO> items;
}
