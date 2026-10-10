package cn.boheng.frame.module.flow.service.relation;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationRespVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationSaveReqVO;
import jakarta.validation.Valid;

/**
 * 流程和步骤的关联。只负责挂上、卸下和调整顺序。
 */
public interface FlowRelationService {

    /**
     * 把启用中的步骤挂到草稿末尾。
     *
     * @return 关联编号
     */
    Long createRelation(@Valid FlowRelationSaveReqVO reqVO);

    /**
     * 从草稿上卸下一条关联。步骤本身还在。
     */
    void deleteRelation(Long id);

    /**
     * 在同一条草稿里上移或下移。offset 为 -1 或 1。
     */
    void moveRelation(Long id, int offset);

    /**
     * 分页。带出流程和步骤上的名称、设备、动作。
     */
    PageResult<FlowRelationRespVO> getRelationPage(FlowRelationPageReqVO reqVO);

}
