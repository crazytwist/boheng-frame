package cn.boheng.frame.module.device.service.measurement;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.measurement.vo.DeviceMeasurementPageReqVO;
import cn.boheng.frame.module.device.controller.admin.measurement.vo.DeviceMeasurementRespVO;
import cn.boheng.frame.module.device.dal.dataobject.measurement.DeviceMeasurementDO;
import cn.boheng.frame.module.device.dal.mysql.analysis.DeviceAnalysisResultMapper;
import cn.boheng.frame.module.device.dal.mysql.measurement.DeviceMeasurementDataMapper;
import cn.boheng.frame.module.device.dal.mysql.measurement.DeviceMeasurementMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_MEASUREMENT_NOT_EXISTS;

@Service
public class DeviceMeasurementServiceImpl implements DeviceMeasurementService {

    @Resource
    private DeviceMeasurementMapper deviceMeasurementMapper;
    @Resource
    private DeviceMeasurementDataMapper deviceMeasurementDataMapper;
    @Resource
    private DeviceAnalysisResultMapper deviceAnalysisResultMapper;

    @Override
    public PageResult<DeviceMeasurementRespVO> getMeasurementPage(DeviceMeasurementPageReqVO pageReqVO) {
        PageResult<DeviceMeasurementDO> page = deviceMeasurementMapper.selectPage(pageReqVO);
        return BeanUtils.toBean(page, DeviceMeasurementRespVO.class);
    }

    @Override
    public DeviceMeasurementRespVO getMeasurement(Long id) {
        DeviceMeasurementDO measurement = deviceMeasurementMapper.selectById(id);
        if (measurement == null) {
            throw exception(DEVICE_MEASUREMENT_NOT_EXISTS);
        }
        DeviceMeasurementRespVO vo = BeanUtils.toBean(measurement, DeviceMeasurementRespVO.class);
        vo.setWells(BeanUtils.toBean(deviceMeasurementDataMapper.selectByMeasurementId(id), DeviceMeasurementRespVO.Well.class));
        vo.setAnalyses(BeanUtils.toBean(deviceAnalysisResultMapper.selectByMeasurementId(id), DeviceMeasurementRespVO.Analysis.class));
        return vo;
    }

}
