package cn.boheng.frame.module.flow.dal.mysql.definition;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.mybatis.core.mapper.BaseMapperX;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationPageReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowNodeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * 流程节点表。
 */
@Mapper
public interface FlowNodeMapper extends BaseMapperX<FlowNodeDO> {

    /**
     * 一个版本的全部步骤，按直线顺序。
     */
    default List<FlowNodeDO> selectByDefinitionId(Long definitionId) {
        return selectList(new LambdaQueryWrapperX<FlowNodeDO>()
                .eq(FlowNodeDO::getDefinitionId, definitionId)
                .orderByAsc(FlowNodeDO::getSortNo)
                .orderByAsc(FlowNodeDO::getId));
    }

    /**
     * 列表页一次查出本页各版本的步骤，用来数步数，避免逐行查询。
     */
    default List<FlowNodeDO> selectByDefinitionIds(Collection<Long> definitionIds) {
        return selectList(new LambdaQueryWrapperX<FlowNodeDO>()
                .in(FlowNodeDO::getDefinitionId, definitionIds));
    }

    /**
     * 关联列表。可以按某一个流程版本过滤。
     */
    default PageResult<FlowNodeDO> selectPage(FlowRelationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FlowNodeDO>()
                .eqIfPresent(FlowNodeDO::getDefinitionId, reqVO.getDefinitionId())
                .orderByAsc(FlowNodeDO::getDefinitionId)
                .orderByAsc(FlowNodeDO::getSortNo)
                .orderByAsc(FlowNodeDO::getId));
    }

    /**
     * 这条步骤还被哪些流程引用。删除和锁定时用。
     */
    default List<FlowNodeDO> selectByStepId(Long stepId) {
        return selectList(FlowNodeDO::getStepId, stepId);
    }

    /**
     * 删除草稿时一并去掉关联。逻辑删除后，生成列会释放节点编码。
     */
    default void deleteByDefinitionId(Long definitionId) {
        delete(new LambdaQueryWrapperX<FlowNodeDO>()
                .eq(FlowNodeDO::getDefinitionId, definitionId));
    }

}
