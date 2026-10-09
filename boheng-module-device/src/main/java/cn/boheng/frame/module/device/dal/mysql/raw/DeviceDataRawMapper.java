package cn.boheng.frame.module.device.dal.mysql.raw;

import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.module.device.dal.dataobject.raw.DeviceDataRawDO;
import org.apache.ibatis.annotations.Mapper;
/**
 * 原始响应 Mapper
 */

@Mapper
public interface DeviceDataRawMapper extends BaseMapperX<DeviceDataRawDO> {
}
