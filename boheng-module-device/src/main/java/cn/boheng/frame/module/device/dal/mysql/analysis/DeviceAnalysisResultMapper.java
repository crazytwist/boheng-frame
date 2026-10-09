package cn.boheng.frame.module.device.dal.mysql.analysis;

import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.module.device.dal.dataobject.analysis.DeviceAnalysisResultDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
/**
 * 分析结论 Mapper
 */

@Mapper
public interface DeviceAnalysisResultMapper extends BaseMapperX<DeviceAnalysisResultDO> {

    /** 按测量编号查询分析结论 */
    default List<DeviceAnalysisResultDO> selectByMeasurementId(Long measurementId) {
        return selectList(DeviceAnalysisResultDO::getMeasurementId, measurementId);
    }

}
