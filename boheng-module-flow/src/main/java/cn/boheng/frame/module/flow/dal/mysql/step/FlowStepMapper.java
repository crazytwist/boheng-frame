package cn.boheng.frame.module.flow.dal.mysql.step;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.flow.controller.admin.step.vo.FlowStepPageReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.step.FlowStepDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 实验步骤表。
 */
@Mapper
public interface FlowStepMapper extends BaseMapperX<FlowStepDO> {

    /**
     * 按编码、名称、设备、状态分页。
     */
    default PageResult<FlowStepDO> selectPage(FlowStepPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FlowStepDO>()
                .likeIfPresent(FlowStepDO::getStepKey, reqVO.getStepKey())
                .likeIfPresent(FlowStepDO::getName, reqVO.getName())
                .eqIfPresent(FlowStepDO::getDeviceCode, reqVO.getDeviceCode())
                .eqIfPresent(FlowStepDO::getStatus, reqVO.getStatus())
                .orderByDesc(FlowStepDO::getId));
    }

    /**
     * 按编码取一条。创建时用来拒绝重复。
     */
    default FlowStepDO selectByStepKey(String stepKey) {
        return selectOne(FlowStepDO::getStepKey, stepKey);
    }

    /**
     * 下拉框。只返回还能被新流程引用的步骤。
     */
    default List<FlowStepDO> selectEnabledList() {
        return selectList(new LambdaQueryWrapperX<FlowStepDO>()
                .eq(FlowStepDO::getStatus, 0)
                .orderByDesc(FlowStepDO::getId));
    }

}
