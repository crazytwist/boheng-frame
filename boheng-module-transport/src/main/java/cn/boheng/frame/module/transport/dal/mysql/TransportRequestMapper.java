package cn.boheng.frame.module.transport.dal.mysql;

import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.module.transport.dal.dataobject.TransportRequestDO;
import org.apache.ibatis.annotations.Mapper;

/** Transport 运输需求 Mapper。 */
@Mapper
public interface TransportRequestMapper extends BaseMapperX<TransportRequestDO> {
}
