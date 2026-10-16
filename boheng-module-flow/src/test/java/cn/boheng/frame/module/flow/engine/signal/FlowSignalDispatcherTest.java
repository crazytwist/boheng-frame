package cn.boheng.frame.module.flow.engine.signal;

import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowExecutionDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepExecutionDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowStepSignalDO;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowExecutionMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowStepExecutionMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowStepSignalMapper;
import cn.boheng.frame.module.flow.engine.execution.FlowExecutionEngine;
import cn.boheng.frame.module.flow.engine.type.StepOutcome;
import cn.boheng.frame.module.flow.service.task.FlowDeviceTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设备任务成功回报和超时不能各结一次：没抢到任务、执行已停、步骤没结算成功时，都不能占住信号号。
 */
class FlowSignalDispatcherTest {

    private FlowSignalDispatcher dispatcher;
    private TaskDouble taskService;
    private EngineDouble engine;
    private SignalDouble signals;
    private String executionStatus = "RUNNING";

    @BeforeEach
    void setUp() {
        dispatcher = new FlowSignalDispatcher();
        taskService = new TaskDouble();
        engine = new EngineDouble();
        signals = new SignalDouble();
        ReflectionTestUtils.setField(dispatcher, "deviceTaskService", taskService);
        ReflectionTestUtils.setField(dispatcher, "engine", engine);
        ReflectionTestUtils.setField(dispatcher, "signalMapper", signals.mapper());
        ReflectionTestUtils.setField(dispatcher, "stepExecutionMapper", waitingStepMapper());
        ReflectionTestUtils.setField(dispatcher, "executionMapper", executionMapper());
    }

    @Test
    void successCallbackStopsWhenTaskWasAlreadyClosed() {
        taskService.finishResult = false;

        assertFalse(dispatcher.deviceTaskSignal("task_1", "token", true, "{}", null));

        assertEquals(0, engine.settleCalls);
        assertEquals(0, signals.inserts);
    }

    @Test
    void successCallbackDoesNotKeepSignalWhenSettleLoses() {
        taskService.finishResult = true;
        engine.settleResult = false;

        assertFalse(dispatcher.deviceTaskSignal("task_1", "token", true, "{}", null));

        assertEquals(1, engine.settleCalls);
        assertEquals(0, signals.inserts);
    }

    @Test
    void successCallbackSavesSignalOnlyAfterSettle() {
        taskService.finishResult = true;
        engine.settleResult = true;

        assertTrue(dispatcher.deviceTaskSignal("task_1", "token", true, "{}", null));

        assertEquals(1, taskService.finishCalls);
        assertEquals(1, engine.settleCalls);
        assertEquals(1, signals.inserts);
    }

    @Test
    void cancelledExecutionDoesNotFinishTheTask() {
        executionStatus = "CANCELLED";

        assertFalse(dispatcher.deviceTaskSignal("task_1", "token", true, "{}", null));

        assertEquals(0, taskService.finishCalls);
        assertEquals(0, engine.settleCalls);
        assertEquals(0, signals.inserts);
    }

    @Test
    void timeoutStopsWhenTaskWasAlreadyClosed() {
        taskService.timeoutResult = false;

        dispatcher.deviceTaskTimeout(1L, "node_a", 1);

        assertEquals(0, engine.settleCalls);
        assertEquals(0, signals.inserts);
    }

    private FlowStepExecutionMapper waitingStepMapper() {
        FlowStepExecutionDO row = new FlowStepExecutionDO();
        row.setStatus("WAITING");
        row.setWaitingFor("DEVICE_CALLBACK");
        row.setAttempt(1);
        row.setIterationNo(0);
        row.setNodeId("node_a");
        return proxy(FlowStepExecutionMapper.class, (method, args) -> {
            if ("selectLatestAttempt".equals(method.getName())) {
                return row;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private FlowExecutionMapper executionMapper() {
        return proxy(FlowExecutionMapper.class, (method, args) -> {
            if ("selectById".equals(method.getName())) {
                FlowExecutionDO execution = new FlowExecutionDO();
                execution.setId(1L);
                execution.setStatus(executionStatus);
                return execution;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static <T> T proxy(Class<T> type, Call call) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (unused, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> type.getSimpleName();
                            case "hashCode" -> System.identityHashCode(unused);
                            case "equals" -> unused == args[0];
                            default -> null;
                        };
                    }
                    return call.invoke(method, args);
                });
        return type.cast(proxy);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }

    @FunctionalInterface
    private interface Call {
        Object invoke(java.lang.reflect.Method method, Object[] args);
    }

    private static final class TaskDouble extends FlowDeviceTaskService {
        private boolean finishResult;
        private boolean timeoutResult;
        private int finishCalls;

        @Override
        public FlowDeviceTaskDO selectByTaskNo(String taskNo) {
            FlowDeviceTaskDO task = new FlowDeviceTaskDO();
            task.setTaskNo("task_1");
            task.setCallbackToken("token");
            task.setExecutionId(1L);
            task.setNodeId("node_a");
            task.setAttempt(1);
            task.setIterationNo(0);
            return task;
        }

        @Override
        public boolean matchesToken(FlowDeviceTaskDO task, String callbackToken) {
            return "token".equals(callbackToken);
        }

        @Override
        public boolean finish(String taskNo, boolean success, String resultJson, String errorMessage) {
            finishCalls++;
            return finishResult;
        }

        @Override
        public boolean timeout(Long executionId, String nodeId, Integer attempt) {
            return timeoutResult;
        }
    }

    private static final class EngineDouble extends FlowExecutionEngine {
        private boolean settleResult;
        private int settleCalls;

        @Override
        public boolean settle(FlowStepExecutionDO row, StepOutcome outcome) {
            settleCalls++;
            return settleResult;
        }
    }

    private static final class SignalDouble {
        private int inserts;

        private FlowStepSignalMapper mapper() {
            return proxy(FlowStepSignalMapper.class, (method, args) -> {
                if ("selectBySignalId".equals(method.getName())) {
                    return null;
                }
                if ("insert".equals(method.getName()) && args != null && args[0] instanceof FlowStepSignalDO) {
                    inserts++;
                    return 1;
                }
                return defaultValue(method.getReturnType());
            });
        }
    }

}
