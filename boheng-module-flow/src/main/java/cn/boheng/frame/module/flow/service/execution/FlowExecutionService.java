package cn.boheng.frame.module.flow.service.execution;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowExecutionPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowExecutionRespVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowExecutionStartReqVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowSignalManualReqVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowStepExecutionPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowStepExecutionRespVO;
import jakarta.validation.Valid;

/**
 * 流程运行对外服务。启动只收已发布版本；取消与人工确认在这里收口校验，
 * 推进细节在引擎里。查询层供运行列表、详情时间线和分支决策回放。
 */
public interface FlowExecutionService {

    /**
     * 启动一次运行。
     *
     * @return 运行编号
     */
    Long startExecution(@Valid FlowExecutionStartReqVO reqVO);

    /**
     * 取消进行中的运行。未开始的节点全部置 CANCELLED。
     */
    void cancelExecution(Long id);

    /**
     * 人工确认 / 拒绝一个等待人工的节点。
     */
    void manualConfirm(@Valid FlowSignalManualReqVO reqVO);

    /**
     * 运行详情，含节点时间线与分支决策。
     */
    FlowExecutionRespVO getExecution(Long id);

    /**
     * 运行分页。
     */
    PageResult<FlowExecutionRespVO> getExecutionPage(FlowExecutionPageReqVO reqVO);

    /**
     * 节点执行分页（时间线）。
     */
    PageResult<FlowStepExecutionRespVO> getStepExecutionPage(FlowStepExecutionPageReqVO reqVO);

}
