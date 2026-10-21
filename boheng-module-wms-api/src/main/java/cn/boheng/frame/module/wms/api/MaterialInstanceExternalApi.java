package cn.boheng.frame.module.wms.api;

import java.util.Collection;
import java.util.List;

/**
 * 物料实例对外契约（清华设备任务 labware 组装等）。
 *
 * <p>实现位于 boheng-module-wms，flow 等模块只依赖本接口。</p>
 */
public interface MaterialInstanceExternalApi {

    /**
     * 按样本业务编号查「使用中」实例，返回清华 9 位器具 id 列表（去重、保序）。
     *
     * @param sampleCodes 样本编号（对应 wms_material_instance.sample_id）
     */
    List<Integer> listEncodedUsingLabwareIdsBySampleCodes(Collection<String> sampleCodes);

    /**
     * 按样本/工单键 + 液体类型（{@code ExLiquidTypeEnum.name()} 存于 content_def_code）查第一个器具的 9 位 id。
     *
     * @param sampleOrBatchKey 样本编号或工单关联键（与实例 sample_id 匹配）
     * @param liquidTypeName   如 {@code LT_Transfection_Liquid}
     */
    Integer findFirstEncodedLabwareId(String sampleOrBatchKey, String liquidTypeName);

}
