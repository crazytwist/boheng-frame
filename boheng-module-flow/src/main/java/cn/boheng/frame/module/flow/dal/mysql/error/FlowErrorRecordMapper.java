package cn.boheng.frame.module.flow.dal.mysql.error;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.flow.controller.admin.error.vo.FlowErrorRecordPageReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.error.FlowErrorRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FlowErrorRecordMapper extends BaseMapperX<FlowErrorRecordDO> {

    default FlowErrorRecordDO selectByDedupKey(String dedupKey) {
        return selectOne(FlowErrorRecordDO::getDedupKey, dedupKey);
    }

    default PageResult<FlowErrorRecordDO> selectPage(FlowErrorRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FlowErrorRecordDO>()
                .eqIfPresent(FlowErrorRecordDO::getExecutionId, reqVO.getExecutionId())
                .eqIfPresent(FlowErrorRecordDO::getNodeId, reqVO.getNodeId())
                .eqIfPresent(FlowErrorRecordDO::getStatus, reqVO.getStatus())
                .eqIfPresent(FlowErrorRecordDO::getCategory, reqVO.getCategory())
                .eqIfPresent(FlowErrorRecordDO::getSourceType, reqVO.getSourceType())
                .likeIfPresent(FlowErrorRecordDO::getErrorNo, reqVO.getErrorNo())
                .orderByDesc(FlowErrorRecordDO::getOccurredAt)
                .orderByDesc(FlowErrorRecordDO::getId));
    }
}
