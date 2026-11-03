package cn.boheng.frame.module.flow.engine.execution;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.error.vo.FlowErrorRecordPageReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.error.FlowErrorActionDO;
import cn.boheng.frame.module.flow.dal.dataobject.error.FlowErrorRecordDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import cn.boheng.frame.module.flow.service.error.FlowErrorRecordService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase E-3：settle 埋点经私有 recordStepError 委托 FlowErrorRecordService。
 */
class FlowExecutionEngineErrorRecordTest {

    @Test
    void recordStepErrorDelegatesWillRetry() {
        FlowExecutionEngine engine = new FlowExecutionEngine();
        List<String> calls = new ArrayList<>();
        FlowErrorRecordService stub = new FlowErrorRecordService() {
            @Override
            public FlowErrorRecordDO recordStepFailure(FlowStepExecutionDO stepRow, String errorMessage, boolean willRetry) {
                calls.add(stepRow.getNodeId() + "|" + errorMessage + "|" + willRetry);
                return null;
            }

            @Override
            public FlowErrorRecordDO getError(Long id) {
                return null;
            }

            @Override
            public PageResult<FlowErrorRecordDO> getErrorPage(FlowErrorRecordPageReqVO pageReqVO) {
                return PageResult.empty();
            }

            @Override
            public List<FlowErrorActionDO> getActions(Long errorId) {
                return Collections.emptyList();
            }
        };
        ReflectionTestUtils.setField(engine, "errorRecordService", stub);

        FlowStepExecutionDO row = FlowStepExecutionDO.builder()
                .id(1L).executionId(9L).nodeId("n1").iterationNo(0).attempt(1).build();
        ReflectionTestUtils.invokeMethod(engine, "recordStepError", row, "库存不足", true);

        assertEquals(1, calls.size());
        assertEquals("n1|库存不足|true", calls.get(0));
    }
}
