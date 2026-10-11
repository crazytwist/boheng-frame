package cn.boheng.frame.module.flow.dal.mysql.execution;

import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskEventDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备任务事件流水表。INSERT-ONLY，只增不删。
 */
@Mapper
public interface FlowDeviceTaskEventMapper extends BaseMapperX<FlowDeviceTaskEventDO> {
}
