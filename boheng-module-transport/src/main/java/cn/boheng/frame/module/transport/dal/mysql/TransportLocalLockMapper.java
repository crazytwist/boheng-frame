package cn.boheng.frame.module.transport.dal.mysql;

import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.module.transport.dal.dataobject.TransportLocalLockDO;
import org.apache.ibatis.annotations.Mapper;

/** Transport 本地锁 Mapper。 */
@Mapper
public interface TransportLocalLockMapper extends BaseMapperX<TransportLocalLockDO> {
}
