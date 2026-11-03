package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 物料可用性检查结果（整批 ok = 全部 available）。
 */
@Data
public class MaterialCheckRespDTO implements Serializable {

    /** 全部明细均可用 */
    private Boolean ok;

    private List<MaterialCheckItemResultDTO> items;

}
