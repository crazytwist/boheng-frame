package cn.boheng.frame.module.flow.service.error;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.error.vo.FlowErrorRecordPageReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.error.FlowErrorActionDO;
import cn.boheng.frame.module.flow.dal.dataobject.error.FlowErrorRecordDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import cn.boheng.frame.module.flow.dal.mysql.error.FlowErrorActionMapper;
import cn.boheng.frame.module.flow.dal.mysql.error.FlowErrorRecordMapper;
import cn.boheng.frame.module.flow.enums.error.FlowErrorActionType;
import cn.boheng.frame.module.flow.enums.error.FlowErrorStatus;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 错误记录写入与只读查询（doc/07 E-2 / E-3）。
 */
@Service
@Validated
public class FlowErrorRecordServiceImpl implements FlowErrorRecordService {

    private static final Logger log = LoggerFactory.getLogger(FlowErrorRecordServiceImpl.class);

    private static final String ERROR_NO_PREFIX = "ferr";

    @Resource
    private FlowErrorRecordMapper errorRecordMapper;

    @Resource
    private FlowErrorActionMapper errorActionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FlowErrorRecordDO recordStepFailure(FlowStepExecutionDO stepRow, String errorMessage, boolean willRetry) {
        if (stepRow == null || stepRow.getId() == null) {
            return null;
        }
        String dedupKey = buildStepDedupKey(stepRow);
        FlowErrorRecordDO existing = errorRecordMapper.selectByDedupKey(dedupKey);
        if (existing != null) {
            return existing;
        }

        FlowErrorClassifier.Classified classified = FlowErrorClassifier.classifyStepFailure(errorMessage);
        String message = StrUtil.blankToDefault(StrUtil.maxLength(StrUtil.nullToEmpty(errorMessage), 500), "节点失败");
        LocalDateTime occurredAt = stepRow.getFinishedAt() != null ? stepRow.getFinishedAt() : LocalDateTime.now();
        String status = willRetry ? FlowErrorStatus.WAITING_RETRY.name() : FlowErrorStatus.OPEN.name();

        FlowErrorRecordDO record = FlowErrorRecordDO.builder()
                .errorNo(ERROR_NO_PREFIX + "_" + IdUtil.getSnowflakeNextIdStr())
                .dedupKey(dedupKey)
                .sourceType(classified.sourceType().name())
                .sourceRefId(String.valueOf(stepRow.getId()))
                .executionId(stepRow.getExecutionId())
                .nodeId(stepRow.getNodeId())
                .iterationNo(stepRow.getIterationNo())
                .attempt(stepRow.getAttempt())
                .commandId(stepRow.getCommandId())
                .errorCode(classified.errorCode())
                .category(classified.category().name())
                .severity(classified.severity().name())
                .certainty(classified.certainty().name())
                .retryMode(classified.retryMode().name())
                .message(message)
                .status(status)
                .occurredAt(occurredAt)
                .build();

        try {
            errorRecordMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            FlowErrorRecordDO raced = errorRecordMapper.selectByDedupKey(dedupKey);
            if (raced != null) {
                return raced;
            }
            throw ex;
        }

        if (willRetry) {
            FlowErrorActionDO action = FlowErrorActionDO.builder()
                    .errorId(record.getId())
                    .action(FlowErrorActionType.RETRY.name())
                    .operator("system")
                    .actionResult("ACCEPTED")
                    .comment("引擎将插入下一 attempt")
                    .build();
            errorActionMapper.insert(action);
        }

        log.info("[flow-error] recorded errorNo={} dedup={} status={} code={}",
                record.getErrorNo(), dedupKey, status, record.getErrorCode());
        return record;
    }

    @Override
    public FlowErrorRecordDO getError(Long id) {
        return id == null ? null : errorRecordMapper.selectById(id);
    }

    @Override
    public PageResult<FlowErrorRecordDO> getErrorPage(FlowErrorRecordPageReqVO pageReqVO) {
        return errorRecordMapper.selectPage(pageReqVO);
    }

    @Override
    public List<FlowErrorActionDO> getActions(Long errorId) {
        if (errorId == null) {
            return Collections.emptyList();
        }
        return errorActionMapper.selectListByErrorId(errorId);
    }

    /** 同一节点同一 attempt 只记一条：step:{executionId}:{nodeId}:{iteration}:{attempt} */
    static String buildStepDedupKey(FlowStepExecutionDO stepRow) {
        return "step:" + stepRow.getExecutionId()
                + ":" + StrUtil.nullToEmpty(stepRow.getNodeId())
                + ":" + (stepRow.getIterationNo() == null ? 0 : stepRow.getIterationNo())
                + ":" + (stepRow.getAttempt() == null ? 1 : stepRow.getAttempt());
    }
}
