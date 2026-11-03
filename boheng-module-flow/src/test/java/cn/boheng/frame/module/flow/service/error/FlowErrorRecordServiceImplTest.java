package cn.boheng.frame.module.flow.service.error;

import cn.boheng.frame.module.flow.dal.dataobject.error.FlowErrorActionDO;
import cn.boheng.frame.module.flow.dal.dataobject.error.FlowErrorRecordDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import cn.boheng.frame.module.flow.dal.mysql.error.FlowErrorActionMapper;
import cn.boheng.frame.module.flow.dal.mysql.error.FlowErrorRecordMapper;
import cn.boheng.frame.module.flow.enums.error.FlowErrorActionType;
import cn.boheng.frame.module.flow.enums.error.FlowErrorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase E：错误记录写入与 dedup（Proxy 桩 Mapper，无 Mockito）。
 */
class FlowErrorRecordServiceImplTest {

    private final Map<String, FlowErrorRecordDO> byDedup = new LinkedHashMap<>();
    private final List<FlowErrorActionDO> actions = new ArrayList<>();
    private final AtomicLong idSeq = new AtomicLong(100);
    private boolean failNextInsert;

    private FlowErrorRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        byDedup.clear();
        actions.clear();
        failNextInsert = false;
        service = new FlowErrorRecordServiceImpl();
        ReflectionTestUtils.setField(service, "errorRecordMapper", recordMapper());
        ReflectionTestUtils.setField(service, "errorActionMapper", actionMapper());
    }

    @Test
    void recordStepFailureWillRetryWritesWaitingRetryAndAction() {
        FlowStepExecutionDO step = step();
        FlowErrorRecordDO saved = service.recordStepFailure(step, "连接超时", true);

        assertEquals(FlowErrorStatus.WAITING_RETRY.name(), saved.getStatus());
        assertEquals("step:100:consume_sample:0:1", saved.getDedupKey());
        assertEquals("9001", saved.getSourceRefId());
        assertTrue(saved.getErrorNo().startsWith("ferr_"));
        assertEquals(1, actions.size());
        assertEquals(FlowErrorActionType.RETRY.name(), actions.get(0).getAction());
        assertEquals(saved.getId(), actions.get(0).getErrorId());
    }

    @Test
    void recordStepFailureDeadLeavesOpenWithoutAction() {
        FlowErrorRecordDO saved = service.recordStepFailure(step(), "库存不足", false);
        assertEquals(FlowErrorStatus.OPEN.name(), saved.getStatus());
        assertEquals("FLOW_WMS_BUSINESS", saved.getErrorCode());
        assertTrue(actions.isEmpty());
    }

    @Test
    void recordStepFailureDedupReturnsExisting() {
        FlowErrorRecordDO existing = FlowErrorRecordDO.builder().id(7L).errorNo("ferr_x")
                .dedupKey("step:100:consume_sample:0:1").build();
        byDedup.put(existing.getDedupKey(), existing);

        FlowErrorRecordDO got = service.recordStepFailure(step(), "again", false);
        assertSame(existing, got);
        assertEquals(1, byDedup.size());
    }

    @Test
    void recordStepFailureDuplicateKeyFallsBackToSelect() {
        failNextInsert = true;
        // 第一次 select 空；insert 抛错后 select 需有值——在 insert 捕获路径里我们 seed
        FlowStepExecutionDO step = step();
        // 自定义：insert 抛错时先写入 raced 记录
        FlowErrorRecordDO got = service.recordStepFailure(step, "race", false);
        assertEquals("ferr_raced", got.getErrorNo());
    }

    @Test
    void buildStepDedupKeyStable() {
        assertEquals("step:100:consume_sample:0:1",
                FlowErrorRecordServiceImpl.buildStepDedupKey(step()));
    }

    private FlowStepExecutionDO step() {
        return FlowStepExecutionDO.builder()
                .id(9001L)
                .executionId(100L)
                .nodeId("consume_sample")
                .iterationNo(0)
                .attempt(1)
                .finishedAt(LocalDateTime.of(2026, 11, 2, 1, 0))
                .build();
    }

    private FlowErrorRecordMapper recordMapper() {
        return (FlowErrorRecordMapper) Proxy.newProxyInstance(
                FlowErrorRecordMapper.class.getClassLoader(),
                new Class[]{FlowErrorRecordMapper.class},
                (p, m, a) -> {
                    String name = m.getName();
                    if ("toString".equals(name)) {
                        return "FlowErrorRecordMapperProxy";
                    }
                    if ("selectByDedupKey".equals(name)) {
                        return byDedup.get((String) a[0]);
                    }
                    if ("insert".equals(name)) {
                        FlowErrorRecordDO row = (FlowErrorRecordDO) a[0];
                        if (failNextInsert) {
                            failNextInsert = false;
                            FlowErrorRecordDO raced = FlowErrorRecordDO.builder()
                                    .id(8L).errorNo("ferr_raced").dedupKey(row.getDedupKey()).build();
                            byDedup.put(row.getDedupKey(), raced);
                            throw new DuplicateKeyException("uk_error_dedup");
                        }
                        if (row.getId() == null) {
                            row.setId(idSeq.incrementAndGet());
                        }
                        byDedup.put(row.getDedupKey(), row);
                        return 1;
                    }
                    throw new UnsupportedOperationException(name);
                });
    }

    private FlowErrorActionMapper actionMapper() {
        return (FlowErrorActionMapper) Proxy.newProxyInstance(
                FlowErrorActionMapper.class.getClassLoader(),
                new Class[]{FlowErrorActionMapper.class},
                (p, m, a) -> {
                    String name = m.getName();
                    if ("toString".equals(name)) {
                        return "FlowErrorActionMapperProxy";
                    }
                    if ("insert".equals(name)) {
                        FlowErrorActionDO row = (FlowErrorActionDO) a[0];
                        if (row.getId() == null) {
                            row.setId(idSeq.incrementAndGet());
                        }
                        actions.add(row);
                        return 1;
                    }
                    if ("selectListByErrorId".equals(name)) {
                        Long errorId = (Long) a[0];
                        List<FlowErrorActionDO> out = new ArrayList<>();
                        for (FlowErrorActionDO row : actions) {
                            if (errorId.equals(row.getErrorId())) {
                                out.add(row);
                            }
                        }
                        return out;
                    }
                    throw new UnsupportedOperationException(name);
                });
    }
}
