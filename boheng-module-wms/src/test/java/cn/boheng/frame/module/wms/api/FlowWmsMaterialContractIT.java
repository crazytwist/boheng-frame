package cn.boheng.frame.module.wms.api;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.api.dto.MaterialCheckItemDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialCheckReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialCheckRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeItemDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialReleaseReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialUseReleaseRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialUseReqDTO;
import cn.boheng.frame.module.wms.controller.admin.movement.vo.MaterialMovementPageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.dal.dataobject.movement.MaterialMovementDO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;
import cn.boheng.frame.module.wms.dal.mysql.instance.MaterialInstanceMapper;
import cn.boheng.frame.module.wms.dal.mysql.slot.SlotMapper;
import cn.boheng.frame.module.wms.enums.InstanceStatusEnum;
import cn.boheng.frame.module.wms.enums.MovementTypeEnum;
import cn.boheng.frame.module.wms.service.instance.MaterialInstanceServiceImpl;
import cn.boheng.frame.module.wms.service.movement.MaterialMovementService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.MOVEMENT_IDEMPOTENT_DUPLICATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase F / D6-a：Flow↔WMS 契约集成（设备仿真/跳过）。
 *
 * <p>不启 Spring、不绑仪器。以 Flow 约定键
 * {@code flow:{executionId}:{nodeId}:{iterationNo}:{attempt}:{opType}}
 * 经 {@link MaterialApi} 覆盖 check / use / consume 幂等回放 / release。</p>
 */
@Tag("it")
class FlowWmsMaterialContractIT {

    private static final long EXE = 9001L;
    private static final String NODE = "assay_consume";
    private static final int ITER = 0;
    private static final int ATTEMPT = 1;

    /** update 代理区分 use/release 与 consume：consume 调用前设为 true */
    private static final ThreadLocal<Boolean> CONSUME_MODE = ThreadLocal.withInitial(() -> false);

    private final Map<Long, MaterialInstanceDO> instances = new LinkedHashMap<>();
    private final List<MaterialMovementDO> store = new ArrayList<>();
    private final AtomicLong movementIdSeq = new AtomicLong(8000);

    private MaterialApi api;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MaterialInstanceDO.class);
        TableInfoHelper.initTableInfo(assistant, SlotDO.class);
    }

    @BeforeEach
    void setUp() {
        instances.clear();
        store.clear();
        CONSUME_MODE.set(false);
        MaterialInstanceServiceImpl service = new MaterialInstanceServiceImpl();
        ReflectionTestUtils.setField(service, "materialInstanceMapper", instanceMapper());
        ReflectionTestUtils.setField(service, "slotMapper", unusedMapper(SlotMapper.class));
        ReflectionTestUtils.setField(service, "materialMovementService", new RecordingMovementService());
        MaterialApiImpl impl = new MaterialApiImpl();
        ReflectionTestUtils.setField(impl, "materialInstanceService", service);
        api = impl;
    }

    @AfterEach
    void tearDown() {
        CONSUME_MODE.remove();
    }

    @Test
    @DisplayName("Flow 键：check 不通过返回结果对象（不抛错）")
    void checkUnavailableReturnsResultObject() {
        putInstance(1L, "S-bad", InstanceStatusEnum.USED.name(), 0);
        putInstance(2L, "S-ok", InstanceStatusEnum.AVAILABLE.name(), 5);

        MaterialCheckReqDTO req = new MaterialCheckReqDTO();
        MaterialCheckItemDTO bad = new MaterialCheckItemDTO();
        bad.setInstanceId(1L);
        MaterialCheckItemDTO ok = new MaterialCheckItemDTO();
        ok.setInstanceId(2L);
        ok.setMinQty(1);
        req.setItems(List.of(bad, ok));

        MaterialCheckRespDTO resp = api.check(req);
        assertFalse(resp.getOk());
        assertEquals(2, resp.getItems().size());
        assertFalse(resp.getItems().get(0).getAvailable());
        assertTrue(resp.getItems().get(1).getAvailable());
    }

    @Test
    @DisplayName("Flow 键：check→use→consume，同 attempt 同键回放不二次扣减")
    void checkUseConsumeThenIdempotentReplay() {
        putInstance(21L, "REAGENT-21", InstanceStatusEnum.AVAILABLE.name(), 1);

        MaterialCheckReqDTO checkReq = new MaterialCheckReqDTO();
        MaterialCheckItemDTO item = new MaterialCheckItemDTO();
        item.setInstanceId(21L);
        item.setMinQty(1);
        checkReq.setItems(List.of(item));
        assertTrue(api.check(checkReq).getOk());

        String useKey = flowKey("use");
        MaterialUseReqDTO useReq = new MaterialUseReqDTO();
        useReq.setInstanceId(21L);
        useReq.setBizSource("FLOW");
        useReq.setOperatorType("AUTO");
        useReq.setRefType("FLOW_STEP");
        useReq.setRefId(String.valueOf(EXE));
        useReq.setIdempotentKey(useKey);
        MaterialUseReleaseRespDTO useResp = api.use(useReq);
        assertFalse(Boolean.TRUE.equals(useResp.getIdempotentReplay()));
        assertEquals(InstanceStatusEnum.IN_USE.name(), useResp.getAfterStatus());
        assertEquals(InstanceStatusEnum.IN_USE.name(), instances.get(21L).getInstanceStatus());
        assertEquals(useKey + "#0", store.get(0).getIdempotentKey());

        String consumeKey = flowKey("consume");
        MaterialConsumeReqDTO consumeReq = new MaterialConsumeReqDTO();
        consumeReq.setBizSource("FLOW");
        consumeReq.setOperatorType("AUTO");
        consumeReq.setIdempotentKey(consumeKey);
        consumeReq.setOperationId("OP-FLOW-1");
        MaterialConsumeItemDTO cItem = new MaterialConsumeItemDTO();
        cItem.setInstanceId(21L);
        consumeReq.setItems(List.of(cItem));

        CONSUME_MODE.set(true);
        MaterialConsumeRespDTO first = api.consume(consumeReq);
        CONSUME_MODE.set(false);

        assertFalse(Boolean.TRUE.equals(first.getIdempotentReplay()));
        assertEquals(List.of(21L), first.getConsumedInstanceIds());
        assertEquals(InstanceStatusEnum.USED.name(), instances.get(21L).getInstanceStatus());
        assertEquals(consumeKey + "#0", store.get(1).getIdempotentKey());

        CONSUME_MODE.set(true);
        MaterialConsumeRespDTO replay = api.consume(consumeReq);
        CONSUME_MODE.set(false);

        assertTrue(Boolean.TRUE.equals(replay.getIdempotentReplay()));
        assertEquals(first.getMovementIds(), replay.getMovementIds());
        assertEquals(2, store.size());
    }

    @Test
    @DisplayName("Flow 键：use 后 release 释放回 AVAILABLE")
    void useThenReleaseWithFlowKeys() {
        putInstance(31L, "HOLDER-31", InstanceStatusEnum.AVAILABLE.name(), 1);

        MaterialUseReqDTO useReq = new MaterialUseReqDTO();
        useReq.setInstanceId(31L);
        useReq.setBizSource("FLOW");
        useReq.setIdempotentKey(flowKey("use"));
        api.use(useReq);
        assertEquals(InstanceStatusEnum.IN_USE.name(), instances.get(31L).getInstanceStatus());

        MaterialReleaseReqDTO releaseReq = new MaterialReleaseReqDTO();
        releaseReq.setInstanceId(31L);
        releaseReq.setBizSource("FLOW");
        releaseReq.setIdempotentKey(flowKey("release"));
        MaterialUseReleaseRespDTO releaseResp = api.release(releaseReq);
        assertEquals(InstanceStatusEnum.AVAILABLE.name(), releaseResp.getAfterStatus());
        assertEquals(InstanceStatusEnum.AVAILABLE.name(), instances.get(31L).getInstanceStatus());
        assertEquals(MovementTypeEnum.RELEASE.getType(), store.get(1).getMovementType());
        assertEquals(flowKey("release") + "#0", store.get(1).getIdempotentKey());
    }

    private static String flowKey(String opType) {
        return "flow:" + EXE + ":" + NODE + ":" + ITER + ":" + ATTEMPT + ":" + opType;
    }

    private void putInstance(long id, String code, String status, Integer count) {
        MaterialInstanceDO inst = new MaterialInstanceDO();
        inst.setId(id);
        inst.setInstanceCode(code);
        inst.setInstanceStatus(status);
        inst.setCurrentCount(count);
        inst.setRootInstanceId(id);
        instances.put(id, inst);
    }

    private MaterialInstanceMapper instanceMapper() {
        return (MaterialInstanceMapper) Proxy.newProxyInstance(
                MaterialInstanceMapper.class.getClassLoader(),
                new Class[]{MaterialInstanceMapper.class},
                (p, m, a) -> {
                    String name = m.getName();
                    if ("toString".equals(name)) {
                        return "MaterialInstanceMapperProxy";
                    }
                    if ("selectById".equals(name)) {
                        return instances.get((Long) a[0]);
                    }
                    if ("update".equals(name)) {
                        for (MaterialInstanceDO inst : instances.values()) {
                            String st = inst.getInstanceStatus();
                            if (InstanceStatusEnum.AVAILABLE.name().equals(st)
                                    || InstanceStatusEnum.RESERVED.name().equals(st)) {
                                if (Boolean.TRUE.equals(CONSUME_MODE.get())) {
                                    inst.setCurrentCount(0);
                                    inst.setInstanceStatus(InstanceStatusEnum.USED.name());
                                } else {
                                    inst.setInstanceStatus(InstanceStatusEnum.IN_USE.name());
                                }
                                return 1;
                            }
                            if (InstanceStatusEnum.IN_USE.name().equals(st)) {
                                if (Boolean.TRUE.equals(CONSUME_MODE.get())) {
                                    inst.setCurrentCount(0);
                                    inst.setInstanceStatus(InstanceStatusEnum.USED.name());
                                } else {
                                    inst.setInstanceStatus(InstanceStatusEnum.AVAILABLE.name());
                                }
                                return 1;
                            }
                        }
                        return 0;
                    }
                    throw new UnsupportedOperationException(name);
                });
    }

    private static <T> T unusedMapper(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (p, m, a) -> {
                    if ("toString".equals(m.getName())) {
                        return type.getSimpleName();
                    }
                    return null;
                }));
    }

    private final class RecordingMovementService implements MaterialMovementService {
        @Override
        public Long recordMovement(MaterialMovementDO movement) {
            if (movement.getIdempotentKey() != null) {
                for (MaterialMovementDO row : store) {
                    if (movement.getIdempotentKey().equals(row.getIdempotentKey())) {
                        throw exception(MOVEMENT_IDEMPOTENT_DUPLICATE, movement.getIdempotentKey());
                    }
                }
            }
            if (movement.getId() == null) {
                movement.setId(movementIdSeq.incrementAndGet());
            }
            store.add(movement);
            return movement.getId();
        }

        @Override
        public PageResult<MaterialMovementDO> getMovementPage(MaterialMovementPageReqVO pageReqVO) {
            return PageResult.empty();
        }

        @Override
        public List<MaterialMovementDO> getMovementListByInstance(Long instanceId) {
            return List.of();
        }

        @Override
        public List<MaterialMovementDO> getMovementListByRootInstance(Long rootInstanceId) {
            return List.of();
        }

        @Override
        public List<MaterialMovementDO> getRecentMovementListBySlot(Long slotId, Integer limit) {
            return List.of();
        }

        @Override
        public MaterialMovementDO getByIdempotentKey(String idempotentKey) {
            for (MaterialMovementDO row : store) {
                if (idempotentKey != null && idempotentKey.equals(row.getIdempotentKey())) {
                    return row;
                }
            }
            return null;
        }

        @Override
        public List<MaterialMovementDO> listByBusinessIdempotentKey(String businessIdempotentKey) {
            if (businessIdempotentKey == null || businessIdempotentKey.isBlank()) {
                return List.of();
            }
            String prefix = businessIdempotentKey + "#";
            List<MaterialMovementDO> out = new ArrayList<>();
            for (MaterialMovementDO row : store) {
                if (row.getIdempotentKey() != null && row.getIdempotentKey().startsWith(prefix)) {
                    out.add(row);
                }
            }
            return out;
        }
    }
}
