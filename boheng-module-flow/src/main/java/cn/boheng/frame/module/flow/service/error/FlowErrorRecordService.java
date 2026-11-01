package cn.boheng.frame.module.flow.service.error;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.error.vo.FlowErrorRecordPageReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.error.FlowErrorActionDO;
import cn.boheng.frame.module.flow.dal.dataobject.error.FlowErrorRecordDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;

import java.util.List;

/**
 * 流程错误记录（E-2 查询 + E-3 写入）。
 */
public interface FlowErrorRecordService {

    /**
     * 节点失败/死亡时落库；同 dedupKey 返回已有记录（不新增）。
     *
     * @param willRetry 引擎还将插入下一 attempt 时为 true → 状态 WAITING_RETRY 并记 RETRY 动作
     */
    FlowErrorRecordDO recordStepFailure(FlowStepExecutionDO stepRow, String errorMessage, boolean willRetry);

    FlowErrorRecordDO getError(Long id);

    PageResult<FlowErrorRecordDO> getErrorPage(FlowErrorRecordPageReqVO pageReqVO);

    List<FlowErrorActionDO> getActions(Long errorId);
}
