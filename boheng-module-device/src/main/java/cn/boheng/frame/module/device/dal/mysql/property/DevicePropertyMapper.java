package cn.boheng.frame.module.device.dal.mysql.property;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.device.controller.admin.property.vo.DevicePropertyPageReqVO;
import cn.boheng.frame.module.device.dal.dataobject.property.DevicePropertyDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 设备可观测属性定义 Mapper
 *
 * @author yinan
 */
@Mapper
public interface DevicePropertyMapper extends BaseMapperX<DevicePropertyDO> {

    default PageResult<DevicePropertyDO> selectPage(DevicePropertyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DevicePropertyDO>()
                .eqIfPresent(DevicePropertyDO::getDeviceTypeCode, reqVO.getDeviceTypeCode())
                .likeIfPresent(DevicePropertyDO::getPropertyCode, reqVO.getPropertyCode())
                .likeIfPresent(DevicePropertyDO::getPropertyName, reqVO.getPropertyName())
                .eqIfPresent(DevicePropertyDO::getDataType, reqVO.getDataType())
                .eqIfPresent(DevicePropertyDO::getSource, reqVO.getSource())
                .eqIfPresent(DevicePropertyDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(DevicePropertyDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(DevicePropertyDO::getId));
    }

    /**
     * 按「类型 + 属性」查（Service 层唯一性校验用）
     */
    default DevicePropertyDO selectByTypeAndProperty(String deviceTypeCode, String propertyCode) {
        return selectOne(new LambdaQueryWrapperX<DevicePropertyDO>()
                .eq(DevicePropertyDO::getDeviceTypeCode, deviceTypeCode)
                .eq(DevicePropertyDO::getPropertyCode, propertyCode));
    }

    /**
     * 按设备类型查属性列表（遥测刷新 / 按属性选设备用）
     */
    default List<DevicePropertyDO> selectListByTypeCode(String deviceTypeCode) {
        return selectList(DevicePropertyDO::getDeviceTypeCode, deviceTypeCode);
    }

}
