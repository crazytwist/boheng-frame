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
/**
 * 测量记录服务实现
 */

@Service
public class DeviceMeasurementServiceImpl implements DeviceMeasurementService {
    /**
     * 测量记录表
     */

    @Resource
    private DeviceMeasurementMapper deviceMeasurementMapper;
    /**
     * 孔级读数表
     */
    @Resource
    private DeviceMeasurementDataMapper deviceMeasurementDataMapper;
    /**
     * 分析结论表
     */
    @Resource
    private DeviceAnalysisResultMapper deviceAnalysisResultMapper;
    /**
     * 测量记录分页
     */

    @Override
    public PageResult<DeviceMeasurementRespVO> getMeasurementPage(DeviceMeasurementPageReqVO pageReqVO) {
        PageResult<DeviceMeasurementDO> page = deviceMeasurementMapper.selectPage(pageReqVO);
        return BeanUtils.toBean(page, DeviceMeasurementRespVO.class);
    }
    /**
     * 测量详情，包含孔位读数和分析结论
     */

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
