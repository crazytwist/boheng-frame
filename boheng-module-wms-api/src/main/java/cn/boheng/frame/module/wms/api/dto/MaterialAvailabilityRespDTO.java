package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 综合可用性检查结果。 */
@Data
public class MaterialAvailabilityRespDTO implements Serializable {

    /** 全部条目满足 */
    private Boolean ok;

    private List<MaterialAvailabilityShortageDTO> items;
}
