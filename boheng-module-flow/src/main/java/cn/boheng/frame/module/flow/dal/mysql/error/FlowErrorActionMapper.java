package cn.boheng.frame.module.flow.dal.mysql.error;

import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.flow.dal.dataobject.error.FlowErrorActionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface FlowErrorActionMapper extends BaseMapperX<FlowErrorActionDO> {

    default List<FlowErrorActionDO> selectListByErrorId(Long errorId) {
        return selectList(new LambdaQueryWrapperX<FlowErrorActionDO>()
                .eq(FlowErrorActionDO::getErrorId, errorId)
                .orderByAsc(FlowErrorActionDO::getCreateTime)
                .orderByAsc(FlowErrorActionDO::getId));
    }
}
