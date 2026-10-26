package cn.boheng.frame.module.transport.dal.mysql;

import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.module.transport.dal.dataobject.*;
import org.apache.ibatis.annotations.Mapper;

/** Transport 数据访问契约集中声明。实际查询方法随业务逻辑逐步补充。 */
public final class TransportMappers { private TransportMappers() {} }

@Mapper interface TransportOrderMapper extends BaseMapperX<TransportOrderDO> {}
@Mapper interface TransportItemMapper extends BaseMapperX<TransportItemDO> {}
@Mapper interface TransportStopMapper extends BaseMapperX<TransportStopDO> {}
@Mapper interface TransportOperationMapper extends BaseMapperX<TransportOperationDO> {}
@Mapper interface TransportHandoverSessionMapper extends BaseMapperX<TransportHandoverSessionDO> {}
@Mapper interface TransportHandoverEvidenceMapper extends BaseMapperX<TransportHandoverEvidenceDO> {}
@Mapper interface TransportLockRefMapper extends BaseMapperX<TransportLockRefDO> {}
@Mapper interface TransportEventMapper extends BaseMapperX<TransportEventDO> {}
