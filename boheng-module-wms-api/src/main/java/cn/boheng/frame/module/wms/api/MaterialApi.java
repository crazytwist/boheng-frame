package cn.boheng.frame.module.wms.api;

import cn.boheng.frame.module.wms.api.dto.MaterialAvailabilityReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialAvailabilityRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialCheckReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialCheckRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeByContainerReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeReservationReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialReleaseByExecutionReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialReleaseByExecutionRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialReleaseReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialReserveReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialReserveRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialSlotDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialSlotQueryReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialUseReleaseRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialUseReqDTO;

import java.util.List;

/**
 * 物料域对外 API（契约层，供 Flow 等模块调用）。
 *
 * <p>W-2.1/W-2.2：综合可用性、槽位查询、预留/按预留消耗/按执行释放；
 * 保留 Phase C+D 的 {@code check/use/consume/release}。不含 putIn/takeOut/transfer。</p>
 *
 * <p>写操作幂等（D1-a）：同一 {@code idempotentKey} 重复到达时回放首次结果。</p>
 * <p>检查（D4-a）：不通过返回结果对象，不以抛错表达业务拒绝。</p>
 */
public interface MaterialApi {

    /**
     * 上机前 / 消耗前可用性检查（按实例）。整批不通过时 {@code ok=false}，明细带 reason。
     */
    MaterialCheckRespDTO check(MaterialCheckReqDTO reqDTO);

    /**
     * 综合可用性检查（样本/试剂/容器/槽位缺项）。不占用资源。
     */
    MaterialAvailabilityRespDTO checkAvailability(MaterialAvailabilityReqDTO reqDTO);

    /**
     * 槽位只读查询（不锁槽）。
     */
    List<MaterialSlotDTO> listSlots(MaterialSlotQueryReqDTO reqDTO);

    /**
     * 按执行预留一批实例（同批共用 reservationId）。
     */
    MaterialReserveRespDTO reserve(MaterialReserveReqDTO reqDTO);

    /**
     * 最小使用：无归属 AVAILABLE→IN_USE；有归属须校验 reservationId/executionId。
     */
    MaterialUseReleaseRespDTO use(MaterialUseReqDTO reqDTO);

    /**
     * 精确消耗：按实例扣减数量/体积或整件消耗。
     * 对有归属实例须走 {@link #consumeReservation} 或携带匹配所有者。
     */
    MaterialConsumeRespDTO consume(MaterialConsumeReqDTO reqDTO);

    /**
     * 按容器消耗：从父实例可用子实例中挑选 N 个整件消耗。
     * {@code includeReserved=true} 不再领取他人有归属预留。
     */
    MaterialConsumeRespDTO consumeByContainer(MaterialConsumeByContainerReqDTO reqDTO);

    /**
     * 有所有权校验的消耗入口（Flow 推荐路径）。
     */
    MaterialConsumeRespDTO consumeReservation(MaterialConsumeReservationReqDTO reqDTO);

    /**
     * 最小释放：无归属旧路径；有归属须校验所有者。RESERVED/IN_USE→AVAILABLE 并清归属。
     */
    MaterialUseReleaseRespDTO release(MaterialReleaseReqDTO reqDTO);

    /**
     * 按执行号批量释放仍处于 RESERVED 的实例；IN_USE 返回待处置。
     */
    MaterialReleaseByExecutionRespDTO releaseByExecution(MaterialReleaseByExecutionReqDTO reqDTO);

}
