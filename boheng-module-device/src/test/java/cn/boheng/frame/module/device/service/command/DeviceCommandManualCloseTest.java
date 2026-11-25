package cn.boheng.frame.module.device.service.command;

import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandCloseReqVO;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandRespVO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.mysql.command.DeviceCommandMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.boheng.frame.module.device.enums.DeviceEnums.ErrorTypeEnum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 人工结束命令的单元测试。
 *
 * <p>盯三件事：终态不许改、只收 SUCCEEDED/FAILED、以及「设备回调刚好同时到达」时
 * 不能把一条命令结算两次。</p>
 */
class DeviceCommandManualCloseTest {

    /**
     * 内存里的命令表，Proxy 直接读写它
     */
    private final Map<Long, DeviceCommandDO> rows = new HashMap<>();

    /**
     * 收到的事件，用来确认流程侧会被唤醒
     */
    private final List<Object> events = new ArrayList<>();

    /**
     * 下一次 selectById 返回后立刻把库里状态改成这个值，模拟回调抢先
     */
    private Long raceId;

    /**
     * 抢先后的状态
     */
    private String raceStatus;

    /**
     * 被测服务
     */
    private DeviceCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DeviceCommandServiceImpl();
        ReflectionTestUtils.setField(service, "deviceCommandMapper", mapper());
        ReflectionTestUtils.setField(service, "eventPublisher", (ApplicationEventPublisher) events::add);
    }

    /**
     * 等回调期间由运维判定成功：状态、可重试性、备注都要落地，并发出完成事件。
     */
    @Test
    void shouldCloseWaitingCommandAsSucceeded() {
        rows.put(1L, command(1L, "WAITING"));

        DeviceCommandRespVO vo = service.manualClose(request(1L, "SUCCEEDED", true, "设备面板已显示完成"));

        assertEquals("SUCCEEDED", vo.getStatus());
        assertEquals(Boolean.TRUE, rows.get(1L).getRetryable());
        assertTrue(rows.get(1L).getRemark().contains("设备面板已显示完成"));
        assertEquals(1, events.size());
    }

    /**
     * 判定失败时必须给失败分类；不填按 BUSINESS 记，避免出现「失败了但不知道哪一类」。
     */
    @Test
    void shouldDefaultErrorTypeToBusinessOnFailure() {
        rows.put(2L, command(2L, "WAITING"));
        DeviceCommandCloseReqVO req = request(2L, "FAILED", false, "回调丢失");
        req.setErrorType(null);

        service.manualClose(req);

        assertEquals("FAILED", rows.get(2L).getStatus());
        assertEquals(ErrorTypeEnum.BUSINESS.getType(), rows.get(2L).getErrorType());
        assertEquals(Boolean.FALSE, rows.get(2L).getRetryable());
        assertEquals("回调丢失", rows.get(2L).getErrorMsg());
    }

    /**
     * 已经结束的命令不能再改：再改一次会让流程节点结算两遍。
     */
    @Test
    void shouldRejectTerminalCommand() {
        rows.put(3L, command(3L, "SUCCEEDED"));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.manualClose(request(3L, "FAILED", false, "想改")));

        assertTrue(error.getMessage().contains("不可人工结束"));
        assertTrue(events.isEmpty());
    }

    /**
     * 目标是 TIMED_OUT / CANCELLED 时拒绝：这两个状态各有归属，不该由人工在这里产生。
     */
    @Test
    void shouldRejectUnsupportedTargetStatus() {
        rows.put(4L, command(4L, "WAITING"));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.manualClose(request(4L, "TIMED_OUT", false, "想直接标超时")));

        assertTrue(error.getMessage().contains("SUCCEEDED / FAILED"));
        assertEquals("WAITING", rows.get(4L).getStatus());
    }

    /**
     * 条件更新失败说明这期间回调或超时扫描先动手了，本次必须放弃并回报真实状态。
     */
    @Test
    void shouldGiveUpWhenStatusChangedMeanwhile() {
        rows.put(5L, command(5L, "WAITING"));
        raceId = 5L;
        raceStatus = "SUCCEEDED";

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.manualClose(request(5L, "FAILED", false, "慢了一步")));

        assertTrue(error.getMessage().contains("SUCCEEDED"));
        assertTrue(events.isEmpty());
    }

    /**
     * 命令不存在时按参数错误报出来，不是静默成功。
     */
    @Test
    void shouldRejectMissingCommand() {
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.manualClose(request(99L, "FAILED", false, "手滑")));

        assertTrue(error.getMessage().contains("设备命令不存在"));
    }

    /**
     * 造一条命令
     */
    private static DeviceCommandDO command(Long id, String status) {
        DeviceCommandDO row = new DeviceCommandDO();
        row.setId(id);
        row.setCommandNo("C" + id);
        row.setStatus(status);
        row.setRetryable(null);
        return row;
    }

    /**
     * 造一次请求
     */
    private static DeviceCommandCloseReqVO request(Long id, String status, boolean retryable, String remark) {
        DeviceCommandCloseReqVO req = new DeviceCommandCloseReqVO();
        req.setId(id);
        req.setStatus(status);
        req.setRetryable(retryable);
        req.setErrorType(ErrorTypeEnum.BUSINESS.getType());
        req.setRemark(remark);
        return req;
    }

    /**
     * 只实现 selectById 与 updateIfStatus 的 Mapper 桩。
     *
     * <p>selectById 返回快照而不是库里那个对象，否则「查出来之后被改」这种竞态在单测里
     * 会变成「查出来就是新值」，测不到条件更新那一步。</p>
     */
    private DeviceCommandMapper mapper() {
        return (DeviceCommandMapper) Proxy.newProxyInstance(
                DeviceCommandMapper.class.getClassLoader(),
                new Class[]{DeviceCommandMapper.class},
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        Long id = (Long) args[0];
                        DeviceCommandDO snapshot = copy(rows.get(id));
                        applyRace(id);
                        return snapshot;
                    }
                    if ("updateIfStatus".equals(method.getName())) {
                        DeviceCommandDO update = (DeviceCommandDO) args[0];
                        String expect = (String) args[1];
                        DeviceCommandDO current = rows.get(update.getId());
                        if (current == null || !expect.equals(current.getStatus())) {
                            return false;
                        }
                        current.setStatus(update.getStatus());
                        current.setWaitingFor(update.getWaitingFor());
                        current.setFinishedAt(update.getFinishedAt());
                        current.setRetryable(update.getRetryable());
                        current.setRemark(update.getRemark());
                        if (update.getErrorType() != null) {
                            current.setErrorType(update.getErrorType());
                        }
                        if (update.getErrorMsg() != null) {
                            current.setErrorMsg(update.getErrorMsg());
                        }
                        return true;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /**
     * 模拟并发抢先：只在指定那一条上生效一次
     */
    private void applyRace(Long id) {
        if (raceId == null || !raceId.equals(id)) {
            return;
        }
        rows.get(id).setStatus(raceStatus);
        raceId = null;
    }

    /**
     * 复制一份，避免调用方持有库里的对象引用
     */
    private static DeviceCommandDO copy(DeviceCommandDO src) {
        if (src == null) {
            return null;
        }
        DeviceCommandDO target = new DeviceCommandDO();
        target.setId(src.getId());
        target.setCommandNo(src.getCommandNo());
        target.setStatus(src.getStatus());
        target.setRetryable(src.getRetryable());
        target.setErrorType(src.getErrorType());
        target.setErrorMsg(src.getErrorMsg());
        target.setRemark(src.getRemark());
        return target;
    }

}
