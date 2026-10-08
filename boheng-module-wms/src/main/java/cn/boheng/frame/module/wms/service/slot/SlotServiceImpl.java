package cn.boheng.frame.module.wms.service.slot;

import cn.hutool.core.util.StrUtil;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.json.JsonUtils;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.slot.vo.SlotPageReqVO;
import cn.boheng.frame.module.wms.controller.admin.slot.vo.SlotSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;
import cn.boheng.frame.module.wms.dal.dataobject.zone.ZoneDO;
import cn.boheng.frame.module.wms.dal.mysql.slot.SlotMapper;
import cn.boheng.frame.module.wms.dal.mysql.zone.ZoneMapper;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.*;

/**
 * 槽位 Service 实现类
 *
 * @author yinan
 */
@Service
@Validated
public class SlotServiceImpl implements SlotService {

    @Resource
    private SlotMapper slotMapper;

    @Resource
    private ZoneMapper zoneMapper;


    @Override
    public Long createSlot(SlotSaveReqVO createReqVO) {
        // 校验槽位编码的唯一性
        validateSlotCodeUnique(createReqVO.getId(), createReqVO.getSlotCode());

        // 插入
        SlotDO slot = BeanUtils.toBean(createReqVO, SlotDO.class);
        slotMapper.insert(slot);
        return slot.getId();
    }


    @Override
    public void updateSlot(SlotSaveReqVO updateReqVO) {
        // 校验存在
        validateSlotExists(updateReqVO.getId());
        // 校验槽位编码的唯一性
        validateSlotCodeUnique(updateReqVO.getId(), updateReqVO.getSlotCode());

        // 更新
        SlotDO updateObj = BeanUtils.toBean(updateReqVO, SlotDO.class);
        slotMapper.updateById(updateObj);
    }

    @Override
    public void deleteSlot(Long id) {
        // 校验存在
        validateSlotExists(id);
        // 删除
        slotMapper.deleteById(id);
    }

    @Override
    public void deleteSlotList(List<Long> ids) {
        slotMapper.deleteBatch(SlotDO::getId, ids);
    }


    @Override
    public SlotDO getSlot(Long id) {
        return slotMapper.selectById(id);
    }

    @Override
    public PageResult<SlotDO> getSlotPage(SlotPageReqVO pageReqVO) {
        return slotMapper.selectPage(pageReqVO);
    }


    @Override
    public List<SlotDO> getSlotList(String zoneCode) {
        return slotMapper.selectListByZoneCode(zoneCode);
    }

    @Override
    public Integer generateSlotsByZone(String zoneCode) {
        // 1. 校验区域存在且为料架类型
        ZoneDO zone = zoneMapper.selectByZoneCode(zoneCode);
        if (zone == null) {
            throw exception(ZONE_NOT_EXISTS);
        }
        if (!"RACK".equals(zone.getZoneType())) {
            throw exception(SLOT_GENERATE_ZONE_NOT_RACK, zoneCode);
        }

        // 2. 解析货架尺寸（ext_data 的 rackRows/rackCols）
        int rackRows = 0;
        int rackCols = 0;
        if (StrUtil.isNotBlank(zone.getExtData())) {
            Map<String, Object> ext = JsonUtils.parseObject(zone.getExtData(), Map.class);
            rackRows = ext.get("rackRows") == null ? 0 : Integer.parseInt(ext.get("rackRows").toString());
            rackCols = ext.get("rackCols") == null ? 0 : Integer.parseInt(ext.get("rackCols").toString());
        }
        if (rackRows <= 0 || rackCols <= 0) {
            throw exception(SLOT_GENERATE_NO_LAYOUT, zoneCode);
        }

        // 3. 按 rowNo=1..rackRows、colNo=1..rackCols 批量生成槽位
        int count = 0;
        for (int row = 1; row <= rackRows; row++) {
            for (int col = 1; col <= rackCols; col++) {
                SlotDO slot = new SlotDO();
                slot.setSlotCode(zoneCode + "_R" + row + "C" + col);
                slot.setZoneCode(zoneCode);
                slot.setSlotType("STORAGE");
                slot.setStatus(0);
                slot.setSlotStatus("FREE");
                slot.setCapacity(1);
                slot.setUniqueBatch(false);
                slot.setOccupiedQty(0);
                slot.setVersion(0);
                // 坐标写入 ext_data
                Map<String, Object> ext = new HashMap<>();
                ext.put("rowNo", row);
                ext.put("colNo", col);
                slot.setExtData(JsonUtils.toJsonString(ext));
                slotMapper.insert(slot);
                count++;
            }
        }
        return count;
    }

    @VisibleForTesting
    void validateSlotExists(Long id) {
        if (slotMapper.selectById(id) == null) {
            throw exception(SLOT_NOT_EXISTS);
        }
    }


    @VisibleForTesting
    void validateSlotCodeUnique(Long id, String slotCode) {
        if (StrUtil.isBlank(slotCode)) {
            return;
        }
        SlotDO slot = slotMapper.selectBySlotCode(slotCode);
        if (slot == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的记录
        if (id == null || !slot.getId().equals(id)) {
            throw exception(SLOT_CODE_DUPLICATE, slotCode);
        }
    }
}
