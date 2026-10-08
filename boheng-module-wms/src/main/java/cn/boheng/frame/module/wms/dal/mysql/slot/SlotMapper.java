package cn.boheng.frame.module.wms.dal.mysql.slot;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.wms.controller.admin.slot.vo.SlotPageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 槽位 Mapper
 *
 * @author yinan
 */
@Mapper
public interface SlotMapper extends BaseMapperX<SlotDO> {

    default PageResult<SlotDO> selectPage(SlotPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<SlotDO>()
                .likeIfPresent(SlotDO::getSlotCode, reqVO.getSlotCode())
                .eqIfPresent(SlotDO::getZoneCode, reqVO.getZoneCode())
                .eqIfPresent(SlotDO::getSlotType, reqVO.getSlotType())
                .eqIfPresent(SlotDO::getStatus, reqVO.getStatus())
                .eqIfPresent(SlotDO::getSlotStatus, reqVO.getSlotStatus())
                .eqIfPresent(SlotDO::getDeviceCode, reqVO.getDeviceCode())
                .betweenIfPresent(SlotDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(SlotDO::getId));
    }

    default SlotDO selectBySlotCode(String slotCode) {
        return selectOne(SlotDO::getSlotCode, slotCode);
    }

    default List<SlotDO> selectListByZoneCode(String zoneCode) {
        return selectList(new LambdaQueryWrapperX<SlotDO>()
                .eqIfPresent(SlotDO::getZoneCode, zoneCode)
                .orderByAsc(SlotDO::getId));
    }
}
