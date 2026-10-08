package cn.boheng.frame.module.device.dal.mysql.measurement;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.device.controller.admin.measurement.vo.DeviceMeasurementPageReqVO;
import cn.boheng.frame.module.device.dal.dataobject.measurement.DeviceMeasurementDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeviceMeasurementMapper extends BaseMapperX<DeviceMeasurementDO> {

    default PageResult<DeviceMeasurementDO> selectPage(DeviceMeasurementPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeviceMeasurementDO>()
                .likeIfPresent(DeviceMeasurementDO::getDeviceCode, reqVO.getDeviceCode())
                .eqIfPresent(DeviceMeasurementDO::getCommandId, reqVO.getCommandId())
                .orderByDesc(DeviceMeasurementDO::getId));
    }

}
