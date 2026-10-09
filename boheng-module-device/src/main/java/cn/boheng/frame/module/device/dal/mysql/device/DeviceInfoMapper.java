package cn.boheng.frame.module.device.dal.mysql.device;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.device.controller.admin.device.vo.DevicePageReqVO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 设备台账 Mapper
 *
 * @author yinan
 */
@Mapper
public interface DeviceInfoMapper extends BaseMapperX<DeviceInfoDO> {

    /** 分页查询设备台账 */
    default PageResult<DeviceInfoDO> selectPage(DevicePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeviceInfoDO>()
                .likeIfPresent(DeviceInfoDO::getDeviceCode, reqVO.getDeviceCode())
                .likeIfPresent(DeviceInfoDO::getDeviceName, reqVO.getDeviceName())
                .eqIfPresent(DeviceInfoDO::getDeviceTypeCode, reqVO.getDeviceTypeCode())
                .eqIfPresent(DeviceInfoDO::getVendor, reqVO.getVendor())
                .eqIfPresent(DeviceInfoDO::getDriverType, reqVO.getDriverType())
                .eqIfPresent(DeviceInfoDO::getStatus, reqVO.getStatus())
                .eqIfPresent(DeviceInfoDO::getConnectionType, reqVO.getConnectionType())
                .betweenIfPresent(DeviceInfoDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DeviceInfoDO::getId));
    }

    /** 按设备编码查询 */
    default DeviceInfoDO selectByDeviceCode(String deviceCode) {
        return selectOne(DeviceInfoDO::getDeviceCode, deviceCode);
    }

    /**
     * 按设备类型查询（「选设备」契约的基础查询）
     */
    default List<DeviceInfoDO> selectListByTypeCode(String deviceTypeCode) {
        return selectList(DeviceInfoDO::getDeviceTypeCode, deviceTypeCode);
    }

}
