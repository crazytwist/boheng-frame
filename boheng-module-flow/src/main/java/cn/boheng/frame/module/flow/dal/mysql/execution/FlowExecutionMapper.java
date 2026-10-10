package cn.boheng.frame.module.flow.dal.mysql.execution;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowExecutionPageReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowExecutionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 流程运行表。
 */
@Mapper
public interface FlowExecutionMapper extends BaseMapperX<FlowExecutionDO> {

    /**
     * 按运行号、流程编码、状态、业务键分页
     */
    default PageResult<FlowExecutionDO> selectPage(FlowExecutionPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FlowExecutionDO>()
                .likeIfPresent(FlowExecutionDO::getExecutionNo, reqVO.getExecutionNo())
                .eqIfPresent(FlowExecutionDO::getPipelineKey, reqVO.getPipelineKey())
                .eqIfPresent(FlowExecutionDO::getStatus, reqVO.getStatus())
                .eqIfPresent(FlowExecutionDO::getBusinessKey, reqVO.getBusinessKey())
                .orderByDesc(FlowExecutionDO::getId));
    }

    /**
     * 一个父节点扇出的全部子运行。FANOUT 收齐判定用
     */
    default List<FlowExecutionDO> selectByParent(Long parentExecutionId, String parentNodeId, Integer parentAttempt) {
        return selectList(new LambdaQueryWrapperX<FlowExecutionDO>()
                .eq(FlowExecutionDO::getParentExecutionId, parentExecutionId)
                .eq(FlowExecutionDO::getParentNodeId, parentNodeId)
                .eq(FlowExecutionDO::getParentAttempt, parentAttempt)
                .orderByAsc(FlowExecutionDO::getParentGroupIndex));
    }

}
