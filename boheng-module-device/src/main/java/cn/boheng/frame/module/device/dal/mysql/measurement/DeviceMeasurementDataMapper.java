package cn.boheng.frame.module.device.dal.mysql.measurement;

import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.device.dal.dataobject.measurement.DeviceMeasurementDataDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DeviceMeasurementDataMapper extends BaseMapperX<DeviceMeasurementDataDO> {

    default List<DeviceMeasurementDataDO> selectByMeasurementId(Long measurementId) {
        return selectList(new LambdaQueryWrapperX<DeviceMeasurementDataDO>()
                .eq(DeviceMeasurementDataDO::getMeasurementId, measurementId)
                .orderByAsc(DeviceMeasurementDataDO::getWellPosition)
                .orderByAsc(DeviceMeasurementDataDO::getWavelengthNm));
    }

}
