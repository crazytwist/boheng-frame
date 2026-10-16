package cn.boheng.frame.module.flow.service.relation;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationRespVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationSaveReqVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationUpdateReqVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationLayoutReqVO;
import jakarta.validation.Valid;

/**
 * 流程版本里的节点：挂能力、建控制类节点、改编排形态、卸下、调顺序。
 * 每次变更后草稿都保持结构合法（前置存在、无环、join 一致）。
 */
public interface FlowRelationService {

    /**
     * 往草稿上加一个节点。挂能力（stepId 非空）或控制类节点（stepType + typeConfig）。
     *
     * @return 关联编号
     */
    Long createRelation(@Valid FlowRelationSaveReqVO reqVO);

    /**
     * 改一个节点的编排形态：前置、汇合、分支出口、参数映射，控制类节点的名称与配置。
     */
    void updateRelation(@Valid FlowRelationUpdateReqVO reqVO);

    /**
     * 从草稿上卸下一条节点。还有节点依赖它时先解除那些依赖。
     */
    void deleteRelation(Long id);

    /**
     * 在同一条草稿里上移或下移。只改展示顺序，不动依赖关系。offset 为 -1 或 1。
     */
    void moveRelation(Long id, int offset);

    /**
     * 批量保存画布坐标。只更新位置，不校验结构（位置不改变图结构）。
     */
    void saveLayout(@Valid FlowRelationLayoutReqVO reqVO);

    /**
     * 分页。带出流程信息与装配后的生效类型、配置、编排字段。
     */
    PageResult<FlowRelationRespVO> getRelationPage(FlowRelationPageReqVO reqVO);

}
