package cn.boheng.frame.module.wms.service.zone;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.zone.vo.ZonePageReqVO;
import cn.boheng.frame.module.wms.controller.admin.zone.vo.ZoneSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;
import cn.boheng.frame.module.wms.dal.dataobject.zone.ZoneDO;
import cn.boheng.frame.module.wms.dal.mysql.slot.SlotMapper;
import cn.boheng.frame.module.wms.dal.mysql.zone.ZoneMapper;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.util.List;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.*;

/**
 * 区域树 Service 实现类
 *
 * @author yinan
 */
@Service
@Validated
public class ZoneServiceImpl implements ZoneService {

    @Resource
    private ZoneMapper zoneMapper;

    /**
     * 直接注入 SlotMapper 而非 SlotService，避免与 SlotService（其内部依赖 ZoneMapper）形成循环依赖
     */
    @Resource
    private SlotMapper slotMapper;


    @Override
    public Long createZone(ZoneSaveReqVO createReqVO) {
        // 校验区域编码的唯一性
        validateZoneCodeUnique(createReqVO.getId(), createReqVO.getZoneCode());

        // 插入
        ZoneDO zone = BeanUtils.toBean(createReqVO, ZoneDO.class);
        zoneMapper.insert(zone);
        return zone.getId();
    }


    @Override
    public void updateZone(ZoneSaveReqVO updateReqVO) {
        // 校验存在
        validateZoneExists(updateReqVO.getId());
        // 校验区域编码的唯一性
        validateZoneCodeUnique(updateReqVO.getId(), updateReqVO.getZoneCode());

        // 更新
        ZoneDO updateObj = BeanUtils.toBean(updateReqVO, ZoneDO.class);
        zoneMapper.updateById(updateObj);
    }

    @Override
    public void deleteZone(Long id) {
        // 校验存在
        ZoneDO zone = validateZoneExists(id);
        // 校验无下层引用（子区域 / 槽位），避免删除后留下悬空的 zone_code 引用
        validateZoneDeletable(zone);
        // 删除
        zoneMapper.deleteById(id);
    }

    @Override
    public void deleteZoneList(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        // 逐个校验（任一不通过则整体拒绝，保证批量删除的原子性语义）
        List<ZoneDO> zones = zoneMapper.selectByIds(ids);
        zones.forEach(this::validateZoneDeletable);
        zoneMapper.deleteBatch(ZoneDO::getId, ids);
    }


    @Override
    public ZoneDO getZone(Long id) {
        return zoneMapper.selectById(id);
    }

    @Override
    public PageResult<ZoneDO> getZonePage(ZonePageReqVO pageReqVO) {
        return zoneMapper.selectPage(pageReqVO);
    }


    @Override
    public List<ZoneDO> getZoneList() {
        return zoneMapper.selectList();
    }

    @VisibleForTesting
    ZoneDO validateZoneExists(Long id) {
        ZoneDO zone = zoneMapper.selectById(id);
        if (zone == null) {
            throw exception(ZONE_NOT_EXISTS);
        }
        return zone;
    }

    /**
     * 校验区域可删除：既不能有子区域，也不能挂载槽位。
     * <p>
     * 区域与下层的关联是「字符串引用边」而非外键 —— 子区域认 {@code parent_zone_code}，
     * 槽位认 {@code zone_code}，两者都只存编码。这里必须显式拦截，
     * 否则删除后下层记录会持有指向不存在区域的悬空编码。
     * <p>
     * 槽位是区域的唯一叶子，槽位清空后其上的物料实例自然也无处可挂，故无需额外校验实例。
     *
     * @param zone 待删除的区域
     */
    @VisibleForTesting
    void validateZoneDeletable(ZoneDO zone) {
        // 1. 子区域
        Long childCount = zoneMapper.selectCount(ZoneDO::getParentZoneCode, zone.getZoneCode());
        if (childCount != null && childCount > 0) {
            throw exception(ZONE_HAS_CHILDREN, zone.getZoneCode(), childCount);
        }
        // 2. 槽位
        Long slotCount = slotMapper.selectCount(SlotDO::getZoneCode, zone.getZoneCode());
        if (slotCount != null && slotCount > 0) {
            throw exception(ZONE_HAS_SLOTS, zone.getZoneCode(), slotCount);
        }
    }


    @VisibleForTesting
    void validateZoneCodeUnique(Long id, String zoneCode) {
        if (StrUtil.isBlank(zoneCode)) {
            return;
        }
        ZoneDO zone = zoneMapper.selectByZoneCode(zoneCode);
        if (zone == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的记录
        if (id == null || !zone.getId().equals(id)) {
            throw exception(ZONE_CODE_DUPLICATE, zoneCode);
        }
    }
}
