package cn.boheng.frame.module.wms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 物料可用性检查请求（D4-a：不通过用结果对象表达，不抛业务拒绝）。
 */
@Data
public class MaterialCheckReqDTO implements Serializable {

    /** 待检查明细 */
    private List<MaterialCheckItemDTO> items;

}
