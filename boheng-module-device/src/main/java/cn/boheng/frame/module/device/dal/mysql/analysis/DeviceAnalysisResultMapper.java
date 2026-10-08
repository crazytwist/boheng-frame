package cn.boheng.frame.module.device.dal.mysql.analysis;

import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.module.device.dal.dataobject.analysis.DeviceAnalysisResultDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface DeviceAnalysisResultMapper extends BaseMapperX<DeviceAnalysisResultDO> {

    default List<DeviceAnalysisResultDO> selectByMeasurementId(Long measurementId) {
        return selectList(DeviceAnalysisResultDO::getMeasurementId, measurementId);
    }

}
