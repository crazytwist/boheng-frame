package cn.boheng.frame.module.wms.api;

import cn.boheng.frame.module.wms.api.dto.TransportSlotQueryReqDTO;
import cn.boheng.frame.module.wms.api.dto.TransportSlotReservationDTO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;
import cn.boheng.frame.module.wms.dal.mysql.slot.SlotMapper;
import cn.boheng.frame.module.wms.enums.SlotStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** WMS 槽位契约实现。 */
@Service
@RequiredArgsConstructor
public class TransportWmsApiImpl implements TransportWmsApi {

    /** WMS 槽位数据访问对象。 */
    private final SlotMapper slotMapper;

    /** 查询区域下空闲且未被物料占用的中转槽位。 */
    @Override
    public List<TransportSlotReservationDTO> listAvailableTransferSlots(TransportSlotQueryReqDTO request) {
        return slotMapper.selectAvailableTransferSlots(request.getZoneCode(), request.getSlotType(),
                        request.getLimit() == null ? 1 : request.getLimit())
                .stream().map(this::toDTO).toList();
    }

    /** 使用 FREE -> LOCKED 的条件更新实现原子预约。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransportSlotReservationDTO reserveTransferSlot(Long slotId, String reservationKey) {
        int updated = slotMapper.update(null, new LambdaUpdateWrapper<SlotDO>()
                .eq(SlotDO::getId, slotId)
                .eq(SlotDO::getSlotStatus, SlotStatusEnum.FREE.name())
                .set(SlotDO::getSlotStatus, SlotStatusEnum.LOCKED.name()));
        if (updated == 0) {
            return null;
        }
        SlotDO slot = slotMapper.selectById(slotId);
        TransportSlotReservationDTO result = toDTO(slot);
        result.setReservationKey(reservationKey);
        return result;
    }

    /** 仅释放仍处于 LOCKED 的槽位，避免覆盖已被 WMS 占用的槽位。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseTransferSlot(Long slotId, String reservationKey) {
        slotMapper.update(null, new LambdaUpdateWrapper<SlotDO>()
                .eq(SlotDO::getId, slotId)
                .eq(SlotDO::getSlotStatus, SlotStatusEnum.LOCKED.name())
                .set(SlotDO::getSlotStatus, SlotStatusEnum.FREE.name()));
    }

    /** 交接确认暂不改变槽位占用，最终位置由 WMS 移动服务更新。 */
    @Override
    public void confirmTransferSlot(Long slotId, String reservationKey) {
        // 槽位状态由 WMS 物料移动流水统一维护，此处保留幂等契约。
    }

    /** 将 WMS 槽位对象转换为跨模块契约对象。 */
    private TransportSlotReservationDTO toDTO(SlotDO slot) {
        return TransportSlotReservationDTO.builder().slotId(slot.getId()).slotCode(slot.getSlotCode())
                .zoneCode(slot.getZoneCode()).slotType(slot.getSlotType()).slotStatus(slot.getSlotStatus())
                .devicePositionNo(slot.getDevicePositionNo())
                .availableCount(Math.max(0, (slot.getCapacity() == null ? 1 : slot.getCapacity())
                        - (slot.getOccupiedQty() == null ? 0 : slot.getOccupiedQty())))
                .build();
    }
}
