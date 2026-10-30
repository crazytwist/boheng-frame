package cn.boheng.frame.module.wms.api;

import cn.boheng.frame.module.wms.api.dto.TransportSlotQueryReqDTO;
import cn.boheng.frame.module.wms.api.dto.TransportSlotReservationDTO;

import java.util.List;

/**
 * WMS 为 Transport 提供的槽位契约。
 *
 * <p>Transport 只通过区域查询位置，不直接访问 WMS 表；实际槽位和占用状态始终以 WMS 为准。</p>
 */
public interface TransportWmsApi {

    /**
     * 查询区域下当前可用于运输的槽位。
     *
     * @param request 区域、槽位类型和数量限制
     * @return 可用槽位快照
     */
    List<TransportSlotReservationDTO> listAvailableTransferSlots(TransportSlotQueryReqDTO request);

    /**
     * 原子锁定一个槽位。返回 null 表示槽位已被其他任务占用。
     *
     * @param slotId 槽位主键
     * @param reservationKey Transport 运行时预约标识
     * @return 锁定后的槽位快照
     */
    TransportSlotReservationDTO reserveTransferSlot(Long slotId, String reservationKey);

    /**
     * 释放运输预占的槽位。
     *
     * @param slotId 槽位主键
     * @param reservationKey Transport 运行时预约标识
     */
    void releaseTransferSlot(Long slotId, String reservationKey);

    /**
     * 交接成功后确认槽位状态，具体物料位置由后续 WMS 移动流水维护。
     *
     * @param slotId 槽位主键
     * @param reservationKey Transport 运行时预约标识
     */
    void confirmTransferSlot(Long slotId, String reservationKey);
}
