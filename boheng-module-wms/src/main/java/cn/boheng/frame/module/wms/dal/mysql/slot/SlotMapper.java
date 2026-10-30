package cn.boheng.frame.module.wms.dal.mysql.slot;

import cn.boheng.frame.framework.common.pojo.PageParam;
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

    /** 查询区域内可作为运输中转位的空闲槽位。 */
    default List<SlotDO> selectAvailableTransferSlots(String zoneCode, String slotType, Integer limit) {
        int size = (limit == null || limit <= 0) ? 1 : Math.min(limit, 200);
        PageParam pageParam = new PageParam();
        pageParam.setPageNo(1);
        pageParam.setPageSize(size);
        return selectPage(pageParam, new LambdaQueryWrapperX<SlotDO>()
                .eq(SlotDO::getZoneCode, zoneCode)
                .eq(SlotDO::getSlotType, slotType)
                .eq(SlotDO::getSlotStatus, "FREE")
                .eq(SlotDO::getStatus, 0)
                .orderByAsc(SlotDO::getId)).getList();
    }

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
