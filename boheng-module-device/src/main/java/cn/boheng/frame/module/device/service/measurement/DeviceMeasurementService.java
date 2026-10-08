package cn.boheng.frame.module.device.service.measurement;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.measurement.vo.DeviceMeasurementPageReqVO;
import cn.boheng.frame.module.device.controller.admin.measurement.vo.DeviceMeasurementRespVO;

public interface DeviceMeasurementService {

    PageResult<DeviceMeasurementRespVO> getMeasurementPage(DeviceMeasurementPageReqVO pageReqVO);

    DeviceMeasurementRespVO getMeasurement(Long id);

}
