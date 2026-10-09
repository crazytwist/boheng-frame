package cn.boheng.frame.module.device.service.measurement;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.measurement.vo.DeviceMeasurementPageReqVO;
import cn.boheng.frame.module.device.controller.admin.measurement.vo.DeviceMeasurementRespVO;

/**
 * 测量记录服务
 */
public interface DeviceMeasurementService {

    /**
     * 测量记录分页
     */
    PageResult<DeviceMeasurementRespVO> getMeasurementPage(DeviceMeasurementPageReqVO pageReqVO);

    /**
     * 测量详情，包含孔位读数和分析结论
     */
    DeviceMeasurementRespVO getMeasurement(Long id);

}
