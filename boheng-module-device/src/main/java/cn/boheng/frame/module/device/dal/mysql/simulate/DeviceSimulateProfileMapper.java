package cn.boheng.frame.module.device.dal.mysql.simulate;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.device.controller.admin.simulate.vo.DeviceSimulateProfilePageReqVO;
import cn.boheng.frame.module.device.dal.dataobject.simulate.DeviceSimulateProfileDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 仿真 HTTP 配置 Mapper
 */
@Mapper
public interface DeviceSimulateProfileMapper extends BaseMapperX<DeviceSimulateProfileDO> {

    /**
     * 分页查询仿真配置
     */
    default PageResult<DeviceSimulateProfileDO> selectPage(DeviceSimulateProfilePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeviceSimulateProfileDO>()
                .eqIfPresent(DeviceSimulateProfileDO::getDeviceId, reqVO.getDeviceId())
                .likeIfPresent(DeviceSimulateProfileDO::getDeviceCode, reqVO.getDeviceCode())
                .eqIfPresent(DeviceSimulateProfileDO::getActionCode, reqVO.getActionCode())
                .eqIfPresent(DeviceSimulateProfileDO::getReturnMode, reqVO.getReturnMode())
                .eqIfPresent(DeviceSimulateProfileDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(DeviceSimulateProfileDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DeviceSimulateProfileDO::getId));
    }

    /**
     * 按设备和动作查配置，含已停用的
     */
    default DeviceSimulateProfileDO selectByDeviceAndAction(Long deviceId, String actionCode) {
        return selectOne(new LambdaQueryWrapperX<DeviceSimulateProfileDO>()
                .eq(DeviceSimulateProfileDO::getDeviceId, deviceId)
                .eq(DeviceSimulateProfileDO::getActionCode, actionCode));
    }

}
