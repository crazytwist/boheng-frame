package cn.boheng.frame.module.device.api;

import cn.boheng.frame.module.device.api.dto.DeviceCommandResultDTO;
import cn.boheng.frame.module.device.api.dto.DeviceCommandSendReqDTO;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeReqVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.mysql.command.DeviceCommandMapper;
import cn.boheng.frame.module.device.dal.mysql.device.DeviceInfoMapper;
import cn.boheng.frame.module.device.service.command.DeviceCommandService;
import cn.boheng.frame.module.device.service.http.DeviceHttpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static cn.boheng.frame.module.device.enums.DeviceEnums.CommandSourceTypeEnum;
import static cn.boheng.frame.module.device.enums.DeviceEnums.CommandStatusEnum;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D01/D02：按来源查令与同键回放的单元测试。
 * 租户隔离按 Mapper 当前租户语义模拟，不启动 Spring，也不改变 WAITING 的 success 语义。
 */
class DeviceCommandApiImplTest {

    /**
     * 当前模拟租户。selectBySource 只返回该租户的命令。
     */
    private Long currentTenantId = 1L;

    /**
     * 已落下的命令，按编号索引。
     */
    private final Map<Long, DeviceCommandDO> commands = new LinkedHashMap<>();

    /**
     * 命令编号序列。
     */
    private final AtomicLong commandIdSeq = new AtomicLong(100);

    /**
     * 真实发往设备的次数，用来确认同键不会二次下发。
     */
    private final AtomicInteger invokeCount = new AtomicInteger();

    /**
     * 为 true 时，invoke 先落入一条命令再抛出唯一键冲突，覆盖并发撞键回放。
     */
    private final AtomicBoolean collideOnInvoke = new AtomicBoolean(false);

    /**
     * 被测命令契约。
     */
    private DeviceCommandApiImpl api;

    /**
     * 注入台账、命令表、命令查询和下发桩，按当前租户隔离来源键。
     */
    @BeforeEach
    void setUp() {
        commands.clear();
        commandIdSeq.set(100);
        invokeCount.set(0);
        collideOnInvoke.set(false);
        currentTenantId = 1L;

        api = new DeviceCommandApiImpl();
        ReflectionTestUtils.setField(api, "deviceInfoMapper", deviceInfoMapper());
        ReflectionTestUtils.setField(api, "deviceHttpService", deviceHttpService());
        ReflectionTestUtils.setField(api, "deviceCommandService", deviceCommandService());
        ReflectionTestUtils.setField(api, "deviceCommandMapper", deviceCommandMapper());
    }

    /**
     * D01：来源键不存在时返回 null，与命令不存在的失败结果区分。
     */
    @Test
    void getResultBySourceReturnsNullWhenMissing() {
        assertNull(api.getResultBySource(CommandSourceTypeEnum.FLOW.getType(), "flow:missing:n:1"));
    }

    /**
     * D01：没有 sourceRef 就没有可回放的业务键。
     */
    @Test
    void getResultBySourceReturnsNullWhenSourceRefBlank() {
        assertNull(api.getResultBySource(CommandSourceTypeEnum.FLOW.getType(), null));
        assertNull(api.getResultBySource(CommandSourceTypeEnum.FLOW.getType(), "  "));
        assertNull(api.getResultBySource(null, null));
    }

    /**
     * D01：进行中的命令按原编号回放当前状态。
     */
    @Test
    void getResultBySourceReturnsInProgressCommand() {
        DeviceCommandDO waiting = putCommand(1L, CommandSourceTypeEnum.FLOW.getType(), "flow:1:n:1", CommandStatusEnum.WAITING.getStatus());

        DeviceCommandResultDTO result = api.getResultBySource(CommandSourceTypeEnum.FLOW.getType(), "flow:1:n:1");

        assertNotNull(result);
        assertEquals(waiting.getId(), result.getCommandId());
        assertEquals(waiting.getCommandNo(), result.getCommandNo());
        assertEquals(CommandStatusEnum.WAITING.getStatus(), result.getStatus());
        assertFalse(result.getSuccess());
    }

    /**
     * D01：终态命令回放成功或失败结果。
     */
    @Test
    void getResultBySourceReturnsTerminalCommand() {
        DeviceCommandDO succeeded = putCommand(1L, CommandSourceTypeEnum.FLOW.getType(), "flow:1:n:1", CommandStatusEnum.SUCCEEDED.getStatus());
        DeviceCommandResultDTO ok = api.getResultBySource(CommandSourceTypeEnum.FLOW.getType(), "flow:1:n:1");
        assertEquals(succeeded.getId(), ok.getCommandId());
        assertEquals(CommandStatusEnum.SUCCEEDED.getStatus(), ok.getStatus());
        assertTrue(ok.getSuccess());

        DeviceCommandDO failed = putCommand(1L, CommandSourceTypeEnum.FLOW.getType(), "flow:1:n:2", CommandStatusEnum.FAILED.getStatus());
        DeviceCommandResultDTO ng = api.getResultBySource(CommandSourceTypeEnum.FLOW.getType(), "flow:1:n:2");
        assertEquals(failed.getId(), ng.getCommandId());
        assertEquals(CommandStatusEnum.FAILED.getStatus(), ng.getStatus());
        assertFalse(ng.getSuccess());
    }

    /**
     * D01：Mapper 按当前租户过滤后，其他租户的同键命令不可见。
     */
    @Test
    void getResultBySourceHidesOtherTenantCommand() {
        DeviceCommandDO original = putCommand(1L, CommandSourceTypeEnum.FLOW.getType(), "flow:1:n:1", CommandStatusEnum.SUCCEEDED.getStatus());

        currentTenantId = 2L;
        assertNull(api.getResultBySource(CommandSourceTypeEnum.FLOW.getType(), "flow:1:n:1"));

        currentTenantId = 1L;
        DeviceCommandResultDTO result = api.getResultBySource(CommandSourceTypeEnum.FLOW.getType(), "flow:1:n:1");
        assertNotNull(result);
        assertEquals(original.getId(), result.getCommandId());
    }

    /**
     * D02：同一来源键第二次 send 不再次调用设备。
     */
    @Test
    void sendSameSourceDoesNotInvokeDeviceTwice() {
        DeviceCommandSendReqDTO req = sendReq(CommandSourceTypeEnum.FLOW.getType(), "flow:9:n:1");

        DeviceCommandResultDTO first = api.send(req);
        DeviceCommandResultDTO second = api.send(req);

        assertEquals(1, invokeCount.get());
        assertEquals(first.getCommandId(), second.getCommandId());
        assertEquals(CommandStatusEnum.WAITING.getStatus(), second.getStatus());
        assertEquals(1, commands.size());
    }

    /**
     * D02：空白 sourceType 与落库一样记为 MANUAL，带 sourceRef 时可回放。
     */
    @Test
    void sendBlankSourceTypeReplaysAsManual() {
        DeviceCommandResultDTO first = api.send(sendReq(null, "  biz-1  "));
        DeviceCommandResultDTO second = api.send(sendReq("  ", "biz-1"));

        assertEquals(1, invokeCount.get());
        assertEquals(first.getCommandId(), second.getCommandId());
        assertEquals(1, commands.size());
        assertEquals(CommandSourceTypeEnum.MANUAL.getType(), commands.get(first.getCommandId()).getSourceType());
        assertEquals("biz-1", commands.get(first.getCommandId()).getSourceRef());

        DeviceCommandResultDTO byBlank = api.getResultBySource(null, "biz-1");
        DeviceCommandResultDTO byManual = api.getResultBySource(CommandSourceTypeEnum.MANUAL.getType(), "  biz-1  ");
        assertEquals(first.getCommandId(), byBlank.getCommandId());
        assertEquals(first.getCommandId(), byManual.getCommandId());
    }

    /**
     * D02：下发过程撞到已有来源键时回放原命令，而不是把异常收成新的失败。
     */
    @Test
    void sendReplaysWhenInvokeHitsExistingSource() {
        collideOnInvoke.set(true);

        DeviceCommandResultDTO result = api.send(sendReq(CommandSourceTypeEnum.FLOW.getType(), "flow:race:n:1"));

        assertEquals(1, invokeCount.get());
        assertEquals(1, commands.size());
        assertEquals(CommandStatusEnum.WAITING.getStatus(), result.getStatus());
        assertEquals(commands.values().iterator().next().getId(), result.getCommandId());
        assertNull(result.getErrorMessage());
        assertFalse(result.getSuccess());
    }

    /**
     * 构造一次下发请求。
     */
    private DeviceCommandSendReqDTO sendReq(String sourceType, String sourceRef) {
        DeviceCommandSendReqDTO req = new DeviceCommandSendReqDTO();
        req.setDeviceCode("DEV-1");
        req.setActionCode("RUN");
        req.setParamsJson("{}");
        req.setSourceType(sourceType);
        req.setSourceRef(sourceRef);
        req.setAttempt(1);
        return req;
    }

    /**
     * 在指定租户落入一条可按来源键查找的命令。
     */
    private DeviceCommandDO putCommand(Long tenantId, String sourceType, String sourceRef, String status) {
        DeviceCommandDO row = new DeviceCommandDO();
        row.setId(commandIdSeq.incrementAndGet());
        row.setCommandNo("C" + row.getId());
        row.setSourceType(sourceType);
        row.setSourceRef(sourceRef);
        row.setStatus(status);
        row.setTenantId(tenantId);
        if (CommandStatusEnum.FAILED.getStatus().equals(status)) {
            row.setErrorMsg("simulated fail");
        }
        commands.put(row.getId(), row);
        return row;
    }

    /**
     * 按当前租户和来源键查找已落命令。
     */
    private DeviceCommandDO findBySource(String sourceType, String sourceRef) {
        return commands.values().stream()
                .filter(item -> Objects.equals(currentTenantId, item.getTenantId()))
                .filter(item -> Objects.equals(sourceType, item.getSourceType())
                        && Objects.equals(sourceRef, item.getSourceRef()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 只回答按设备编码查台账。
     */
    private DeviceInfoMapper deviceInfoMapper() {
        return (DeviceInfoMapper) Proxy.newProxyInstance(
                DeviceInfoMapper.class.getClassLoader(),
                new Class[]{DeviceInfoMapper.class},
                (proxy, method, args) -> {
                    if ("selectByDeviceCode".equals(method.getName())) {
                        String code = (String) args[0];
                        if (!"DEV-1".equals(code)) {
                            return null;
                        }
                        DeviceInfoDO device = new DeviceInfoDO();
                        device.setId(10L);
                        device.setDeviceCode("DEV-1");
                        return device;
                    }
                    throw unsupported(method.getName());
                });
    }

    /**
     * 只回答当前租户的来源键查询。
     */
    private DeviceCommandMapper deviceCommandMapper() {
        return (DeviceCommandMapper) Proxy.newProxyInstance(
                DeviceCommandMapper.class.getClassLoader(),
                new Class[]{DeviceCommandMapper.class},
                (proxy, method, args) -> {
                    if ("selectBySource".equals(method.getName())) {
                        return findBySource((String) args[0], (String) args[1]);
                    }
                    throw unsupported(method.getName());
                });
    }

    /**
     * 按命令编号回放当前状态，解析结果本测不关心。
     */
    private DeviceCommandService deviceCommandService() {
        return (DeviceCommandService) Proxy.newProxyInstance(
                DeviceCommandService.class.getClassLoader(),
                new Class[]{DeviceCommandService.class},
                (proxy, method, args) -> {
                    if ("getCommand".equals(method.getName())) {
                        Long commandId = (Long) args[0];
                        DeviceCommandDO row = commands.get(commandId);
                        if (row == null) {
                            throw new RuntimeException("命令不存在: " + commandId);
                        }
                        DeviceCommandRespVO vo = new DeviceCommandRespVO();
                        vo.setId(row.getId());
                        vo.setCommandNo(row.getCommandNo());
                        vo.setStatus(row.getStatus());
                        vo.setRawBody(row.getResponseJson());
                        vo.setErrorMsg(row.getErrorMsg());
                        return vo;
                    }
                    if ("getParsedOutput".equals(method.getName())) {
                        return null;
                    }
                    throw unsupported(method.getName());
                });
    }

    /**
     * 记下一次物理下发；需要时模拟插入后撞唯一键。
     */
    private DeviceHttpService deviceHttpService() {
        return new DeviceHttpService() {
            @Override
            public DeviceInvokeRespVO invoke(DeviceInvokeReqVO reqVO, DeviceCommandService.CommandSource source) {
                invokeCount.incrementAndGet();
                DeviceCommandDO row = putCommand(currentTenantId, source.sourceType(), source.sourceRef(), CommandStatusEnum.WAITING.getStatus());
                if (collideOnInvoke.get()) {
                    throw new RuntimeException("Duplicate entry for key uk_source");
                }
                DeviceInvokeRespVO resp = new DeviceInvokeRespVO();
                resp.setStatus(200);
                resp.setCommandId(row.getId());
                resp.setCommandNo(row.getCommandNo());
                resp.setCommandStatus(CommandStatusEnum.WAITING.getStatus());
                resp.setBody("{\"accepted\":true}");
                return resp;
            }
        };
    }

    /**
     * 本测未覆盖的协作方法不应被调用。
     */
    private static UnsupportedOperationException unsupported(String methodName) {
        return new UnsupportedOperationException("unexpected call: " + methodName);
    }

}
