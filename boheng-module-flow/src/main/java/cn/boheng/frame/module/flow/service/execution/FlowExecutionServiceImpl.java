package cn.boheng.frame.module.flow.service.execution;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.framework.security.core.util.SecurityFrameworkUtils;
import cn.boheng.frame.framework.tenant.core.util.TenantUtils;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowBranchDecisionRespVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowExecutionPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowExecutionRespVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowExecutionStartReqVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowSignalManualReqVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowStepExecutionPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowStepExecutionRespVO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowDefinitionDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowBranchDecisionDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowExecutionDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import cn.boheng.frame.module.flow.dal.mysql.definition.FlowDefinitionMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowBranchDecisionMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowDeviceTaskMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowExecutionMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowStepExecutionMapper;
import cn.boheng.frame.module.flow.engine.execution.FlowExecutionEngine;
import cn.boheng.frame.module.flow.engine.signal.FlowSignalDispatcher;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.boheng.frame.module.flow.enums.FlowRuntimeEnums.ExecutionStatus;
import static cn.boheng.frame.module.flow.enums.FlowRuntimeEnums.TriggerType;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_EXECUTION_CANCEL_STATUS;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_EXECUTION_DEFINITION_NOT_PUBLISHED;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_EXECUTION_NOT_EXISTS;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_SIGNAL_NOT_WAITING_MANUAL;

/**
 * 流程运行服务。启动时绑死已发布版本和当时的指纹，防止定义漂移；
 * 取消和人工确认先做状态收口，再交给引擎 / 信号分发器。
 */
@Service
@Validated
public class FlowExecutionServiceImpl implements FlowExecutionService {

    /**
     * 运行表
     */
    @Resource
    private FlowExecutionMapper executionMapper;

    /**
     * 节点执行表
     */
    @Resource
    private FlowStepExecutionMapper stepExecutionMapper;

    /**
     * 分支决策表
     */
    @Resource
    private FlowBranchDecisionMapper branchDecisionMapper;

    /**
     * 流程版本表。启动时确认目标处于已发布状态
     */
    @Resource
    private FlowDefinitionMapper definitionMapper;

    /**
     * 执行引擎
     */
    @Resource
    private FlowExecutionEngine engine;

    /**
     * 信号分发器。人工确认走它
     */
    @Resource
    private FlowSignalDispatcher signalDispatcher;

    /**
     * 设备异步任务表。详情里反查 device.task 节点的任务状态
     */
    @Resource
    private FlowDeviceTaskMapper deviceTaskMapper;

    /**
     * 启动。只有已发布版本能启动；顶层运行没有父节点信息。
     */
    @Override
    public Long startExecution(FlowExecutionStartReqVO reqVO) {
        FlowDefinitionDO definition = definitionMapper.selectById(reqVO.getDefinitionId());
        if (definition == null || !FlowConstants.STATUS_PUBLISHED.equals(definition.getStatus())) {
            throw exception(FLOW_EXECUTION_DEFINITION_NOT_PUBLISHED,
                    definition == null ? "" : definition.getPipelineKey(),
                    definition == null ? 0 : definition.getVersion());
        }
        return engine.start(definition, StrUtil.trimToNull(reqVO.getInputParams()),
                TriggerType.MANUAL.name(), String.valueOf(SecurityFrameworkUtils.getLoginUserId()),
                StrUtil.trimToNull(reqVO.getBusinessKey()), null, null, null, null);
    }

    /**
     * 定时启动。由 JobHandler 按 cron 触发，入参里带租户编号；
     * 在指定租户上下文里查定义、确认已发布后启动，运行来源标 CRON。
     */
    @Override
    public Long startScheduled(Long definitionId, String inputParams, String businessKey, Long tenantId) {
        return TenantUtils.execute(tenantId, () -> {
            FlowDefinitionDO definition = definitionMapper.selectById(definitionId);
            if (definition == null || !FlowConstants.STATUS_PUBLISHED.equals(definition.getStatus())) {
                throw exception(FLOW_EXECUTION_DEFINITION_NOT_PUBLISHED,
                        definition == null ? "" : definition.getPipelineKey(),
                        definition == null ? 0 : definition.getVersion());
            }
            return engine.start(definition, StrUtil.trimToNull(inputParams),
                    TriggerType.CRON.name(), "job", StrUtil.trimToNull(businessKey), null, null, null, null);
        });
    }

    /**
     * 取消。只有进行中的运行能取消。
     */
    @Override
    public void cancelExecution(Long id) {
        FlowExecutionDO execution = requireExecution(id);
        if (!ExecutionStatus.RUNNING.name().equals(execution.getStatus())
                && !ExecutionStatus.PENDING.name().equals(execution.getStatus())) {
            throw exception(FLOW_EXECUTION_CANCEL_STATUS);
        }
        engine.cancel(id);
    }

    /**
     * 人工确认 / 拒绝。节点不在等待人工时拒绝。
     */
    @Override
    public void manualConfirm(FlowSignalManualReqVO reqVO) {
        requireExecution(reqVO.getExecutionId());
        boolean accepted = signalDispatcher.manualSignal(reqVO.getExecutionId(), reqVO.getNodeId(),
                Boolean.TRUE.equals(reqVO.getApprove()), reqVO.getComment(),
                String.valueOf(SecurityFrameworkUtils.getLoginUserId()));
        if (!accepted) {
            throw exception(FLOW_SIGNAL_NOT_WAITING_MANUAL, reqVO.getNodeId());
        }
    }

    /**
     * 详情：运行本体 + 节点时间线 + 分支决策。
     */
    @Override
    public FlowExecutionRespVO getExecution(Long id) {
        FlowExecutionDO execution = requireExecution(id);
        FlowExecutionRespVO resp = BeanUtils.toBean(execution, FlowExecutionRespVO.class);
        List<FlowStepExecutionDO> steps = stepExecutionMapper.selectByExecutionId(id);
        resp.setSteps(steps.stream()
                .map(step -> {
                    FlowStepExecutionRespVO vo = BeanUtils.toBean(step, FlowStepExecutionRespVO.class);
                    enrichDeviceTaskStatus(step, vo);
                    return vo;
                }).toList());
        List<FlowBranchDecisionDO> decisions = branchDecisionMapper.selectByExecutionId(id);
        resp.setBranchDecisions(decisions.stream()
                .map(decision -> BeanUtils.toBean(decision, FlowBranchDecisionRespVO.class)).toList());
        return resp;
    }

    /**
     * device.task 节点的任务状态：节点挂起时 waitRef 就是任务号（taskNo），
     * 用它反查任务队列，把 PENDING / CLAIMED / DONE / FAILED / CANCELLED 带出到预览图。
     */
    private void enrichDeviceTaskStatus(FlowStepExecutionDO step, FlowStepExecutionRespVO vo) {
        if (!FlowConstants.STEP_DEVICE_TASK.equals(step.getStepType())
                || StrUtil.isBlank(step.getWaitRef())) {
            return;
        }
        FlowDeviceTaskDO task = deviceTaskMapper.selectByTaskNo(step.getWaitRef());
        if (task != null) {
            vo.setDeviceTaskStatus(task.getStatus());
        }
    }

    /**
     * 分页。
     */
    @Override
    public PageResult<FlowExecutionRespVO> getExecutionPage(FlowExecutionPageReqVO reqVO) {
        PageResult<FlowExecutionDO> page = executionMapper.selectPage(reqVO);
        return new PageResult<>(
                page.getList().stream().map(row -> BeanUtils.toBean(row, FlowExecutionRespVO.class)).toList(),
                page.getTotal());
    }

    /**
     * 节点执行分页。
     */
    @Override
    public PageResult<FlowStepExecutionRespVO> getStepExecutionPage(FlowStepExecutionPageReqVO reqVO) {
        PageResult<FlowStepExecutionDO> page = stepExecutionMapper.selectPage(reqVO);
        return new PageResult<>(
                page.getList().stream().map(row -> BeanUtils.toBean(row, FlowStepExecutionRespVO.class)).toList(),
                page.getTotal());
    }

    /**
     * 运行不存在时抛错。
     */
    private FlowExecutionDO requireExecution(Long id) {
        FlowExecutionDO execution = executionMapper.selectById(id);
        if (execution == null) {
            throw exception(FLOW_EXECUTION_NOT_EXISTS);
        }
        return execution;
    }

}
