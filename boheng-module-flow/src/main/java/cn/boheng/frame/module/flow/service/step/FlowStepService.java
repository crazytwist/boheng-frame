package cn.boheng.frame.module.flow.service.step;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.step.vo.FlowStepPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.step.vo.FlowStepRespVO;
import cn.boheng.frame.module.flow.controller.admin.step.vo.FlowStepSaveReqVO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 实验步骤。步骤先单独存在，再由流程关联进去。
 */
public interface FlowStepService {

    /**
     * 新建一条步骤。
     *
     * @return 步骤编号
     */
    Long createStep(@Valid FlowStepSaveReqVO reqVO);

    /**
     * 修改步骤。已被已发布或已停用的流程引用时拒绝。
     */
    void updateStep(@Valid FlowStepSaveReqVO reqVO);

    /**
     * 删除步骤。已被任何流程引用时拒绝。
     */
    void deleteStep(Long id);

    /**
     * 取一条步骤。
     */
    FlowStepRespVO getStep(Long id);

    /**
     * 分页。
     */
    PageResult<FlowStepRespVO> getStepPage(FlowStepPageReqVO reqVO);

    /**
     * 启用中的步骤，给关联页下拉用。
     */
    List<FlowStepRespVO> getEnabledStepList();

}
