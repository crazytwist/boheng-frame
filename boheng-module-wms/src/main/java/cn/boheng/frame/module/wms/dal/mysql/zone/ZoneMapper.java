package cn.boheng.frame.module.wms.dal.mysql.zone;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.wms.controller.admin.zone.vo.ZonePageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.zone.ZoneDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/**
 * 区域树 Mapper
 *
 * @author yinan
 */
@Mapper
public interface ZoneMapper extends BaseMapperX<ZoneDO> {

    default PageResult<ZoneDO> selectPage(ZonePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ZoneDO>()
                .likeIfPresent(ZoneDO::getZoneCode, reqVO.getZoneCode())
                .eqIfPresent(ZoneDO::getParentZoneCode, reqVO.getParentZoneCode())
                .likeIfPresent(ZoneDO::getZoneName, reqVO.getZoneName())
                .eqIfPresent(ZoneDO::getZoneType, reqVO.getZoneType())
                .eqIfPresent(ZoneDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(ZoneDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ZoneDO::getId));
    }

    default ZoneDO selectByZoneCode(String zoneCode) {
        return selectOne(ZoneDO::getZoneCode, zoneCode);
    }
}
