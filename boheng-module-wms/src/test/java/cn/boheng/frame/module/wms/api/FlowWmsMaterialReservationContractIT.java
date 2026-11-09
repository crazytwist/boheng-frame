package cn.boheng.frame.module.wms.api;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeItemDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeReservationReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialReleaseByExecutionReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialReleaseByExecutionRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialReserveReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialReserveRespDTO;
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
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.MOVEMENT_IDEMPOTENT_DUPLICATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2.2 / W04：Flow 键经 MaterialApi 覆盖 reserve → consumeReservation / releaseByExecution。
 */
@Tag("it")
class FlowWmsMaterialReservationContractIT {

    private static final String EXE = "9002";
    private static final String NODE = "assay_reserve";
    private static final int ITER = 0;
    private static final int ATTEMPT = 1;

    private final Map<Long, MaterialInstanceDO> instances = new LinkedHashMap<>();
    private final List<MaterialMovementDO> store = new ArrayList<>();
    private final AtomicLong movementIdSeq = new AtomicLong(9000);

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
        MaterialInstanceServiceImpl service = new MaterialInstanceServiceImpl();
        ReflectionTestUtils.setField(service, "materialInstanceMapper", instanceMapper());
        ReflectionTestUtils.setField(service, "slotMapper", unusedMapper(SlotMapper.class));
        ReflectionTestUtils.setField(service, "materialMovementService", new RecordingMovementService());
        MaterialApiImpl impl = new MaterialApiImpl();
        ReflectionTestUtils.setField(impl, "materialInstanceService", service);
        api = impl;
    }

    @Test
    @DisplayName("Flow 键：reserve → consumeReservation 耗尽清归属")
    void reserveThenConsumeReservationWithFlowKeys() {
        putAvailable(41L, "RSV-MAT-41");
        putAvailable(42L, "RSV-MAT-42");

        MaterialReserveReqDTO reserveReq = new MaterialReserveReqDTO();
        reserveReq.setExecutionId(EXE);
        reserveReq.setInstanceIds(List.of(41L, 42L));
        reserveReq.setReservedUntil(LocalDateTime.now().plusHours(1));
        reserveReq.setBizSource("FLOW");
        reserveReq.setOperatorType("AUTO");
        reserveReq.setRefType("FLOW_STEP");
        reserveReq.setRefId(EXE);
        reserveReq.setIdempotentKey(flowKey("reserve"));

        MaterialReserveRespDTO reserved = api.reserve(reserveReq);
        assertFalse(Boolean.TRUE.equals(reserved.getIdempotentReplay()));
        assertNotNull(reserved.getReservationId());
        assertEquals(2, reserved.getInstanceIds().size());
        assertEquals(InstanceStatusEnum.RESERVED.name(), instances.get(41L).getInstanceStatus());
        assertEquals(EXE, instances.get(41L).getReservedByExecutionId());
        assertEquals(2, store.size());
        assertEquals(MovementTypeEnum.RESERVE.getType(), store.get(0).getMovementType());
        assertEquals(flowKey("reserve") + "#0", store.get(0).getIdempotentKey());

        MaterialReserveRespDTO replay = api.reserve(reserveReq);
        assertTrue(Boolean.TRUE.equals(replay.getIdempotentReplay()));
        assertEquals(reserved.getReservationId(), replay.getReservationId());
        assertEquals(2, store.size());

        MaterialConsumeReservationReqDTO consumeReq = new MaterialConsumeReservationReqDTO();
        consumeReq.setReservationId(reserved.getReservationId());
        consumeReq.setExecutionId(EXE);
        consumeReq.setBizSource("FLOW");
        consumeReq.setOperatorType("AUTO");
        consumeReq.setIdempotentKey(flowKey("consumeReservation"));
        MaterialConsumeItemDTO item = new MaterialConsumeItemDTO();
        item.setInstanceId(41L);
        consumeReq.setItems(List.of(item));

        MaterialConsumeRespDTO consumed = api.consumeReservation(consumeReq);
        assertFalse(Boolean.TRUE.equals(consumed.getIdempotentReplay()));
        assertEquals(List.of(41L), consumed.getConsumedInstanceIds());
        assertEquals(InstanceStatusEnum.USED.name(), instances.get(41L).getInstanceStatus());
        assertNull(instances.get(41L).getReservationId());
        // 另一件仍预留
        assertEquals(InstanceStatusEnum.RESERVED.name(), instances.get(42L).getInstanceStatus());
        assertEquals(reserved.getReservationId(), instances.get(42L).getReservationId());
    }

    @Test
    @DisplayName("Flow 键：reserve → releaseByExecution 取消释放仅 RESERVED")
    void reserveThenReleaseByExecutionWithFlowKeys() {
        putAvailable(51L, "RSV-MAT-51");
        putAvailable(52L, "RSV-MAT-52");

        MaterialReserveReqDTO reserveReq = new MaterialReserveReqDTO();
        reserveReq.setExecutionId(EXE);
        reserveReq.setInstanceIds(List.of(51L, 52L));
        reserveReq.setReservedUntil(LocalDateTime.now().plusHours(1));
        reserveReq.setBizSource("FLOW");
        reserveReq.setIdempotentKey(flowKey("reserve"));
        MaterialReserveRespDTO reserved = api.reserve(reserveReq);
        assertEquals(2, reserved.getInstanceIds().size());

        // 将 52 推到 IN_USE，模拟已开始使用；取消时不应自动释放
        instances.get(52L).setInstanceStatus(InstanceStatusEnum.IN_USE.name());

        MaterialReleaseByExecutionReqDTO releaseReq = new MaterialReleaseByExecutionReqDTO();
        releaseReq.setExecutionId(EXE);
        releaseReq.setReason("flow-cancel");
        releaseReq.setBizSource("FLOW");
        releaseReq.setIdempotentKey(flowKey("releaseByExecution"));

        MaterialReleaseByExecutionRespDTO released = api.releaseByExecution(releaseReq);
        assertEquals(List.of(51L), released.getReleasedInstanceIds());
        assertEquals(1, released.getPendingInUse().size());
        assertEquals(52L, released.getPendingInUse().get(0).getInstanceId());
        assertEquals(InstanceStatusEnum.AVAILABLE.name(), instances.get(51L).getInstanceStatus());
        assertNull(instances.get(51L).getReservationId());
        assertEquals(InstanceStatusEnum.IN_USE.name(), instances.get(52L).getInstanceStatus());
        assertTrue(store.stream().anyMatch(m ->
                MovementTypeEnum.RELEASE.getType().equals(m.getMovementType())
                        && flowKey("releaseByExecution").equals(m.getIdempotentKey().split("#")[0])));
    }

    private static String flowKey(String opType) {
        return "flow:" + EXE + ":" + NODE + ":" + ITER + ":" + ATTEMPT + ":" + opType;
    }

    private void putAvailable(long id, String code) {
        MaterialInstanceDO inst = new MaterialInstanceDO();
        inst.setId(id);
        inst.setInstanceCode(code);
        inst.setInstanceStatus(InstanceStatusEnum.AVAILABLE.name());
        inst.setCurrentCount(1);
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
                    if ("selectOwnedByExecutionId".equals(name)) {
                        String executionId = (String) a[0];
                        List<MaterialInstanceDO> out = new ArrayList<>();
                        for (MaterialInstanceDO inst : instances.values()) {
                            if (executionId.equals(inst.getReservedByExecutionId())
                                    && (InstanceStatusEnum.RESERVED.name().equals(inst.getInstanceStatus())
                                    || InstanceStatusEnum.IN_USE.name().equals(inst.getInstanceStatus()))) {
                                out.add(inst);
                            }
                        }
                        return out;
                    }
                    if ("selectExpiredReserved".equals(name)) {
                        LocalDateTime now = (LocalDateTime) a[0];
                        List<MaterialInstanceDO> out = new ArrayList<>();
                        for (MaterialInstanceDO inst : instances.values()) {
                            if (InstanceStatusEnum.RESERVED.name().equals(inst.getInstanceStatus())
                                    && inst.getReservedUntil() != null
                                    && !inst.getReservedUntil().isAfter(now)) {
                                out.add(inst);
                            }
                        }
                        return out;
                    }
                    if ("update".equals(name)) {
                        return applyUpdate((AbstractWrapper<?, ?, ?>) a[1]);
                    }
                    throw new UnsupportedOperationException(name);
                });
    }

    private int applyUpdate(AbstractWrapper<?, ?, ?> wrapper) {
        Map<String, Object> pairs = wrapper.getParamNameValuePairs();
        String sqlSet = wrapper.getSqlSet();
        String segment = String.valueOf(wrapper.getSqlSegment());
        if (sqlSet == null) {
            return 0;
        }
        Map<String, Object> sets = resolveSetMap(sqlSet, pairs);

        Long targetId = null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("id\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(segment);
        if (m.find()) {
            Object v = pairs.get(m.group(1));
            if (v instanceof Long l) {
                targetId = l;
            } else if (v != null) {
                try {
                    targetId = Long.valueOf(String.valueOf(v));
                } catch (Exception ignored) {
                }
            }
        }
        if (targetId == null) {
            String toStatus = sets.get("instance_status") == null ? null : String.valueOf(sets.get("instance_status"));
            for (MaterialInstanceDO inst : instances.values()) {
                if (InstanceStatusEnum.RESERVED.name().equals(toStatus)
                        && InstanceStatusEnum.AVAILABLE.name().equals(inst.getInstanceStatus())
                        && inst.getReservationId() == null) {
                    targetId = inst.getId();
                    break;
                }
                if (InstanceStatusEnum.USED.name().equals(toStatus)
                        && (InstanceStatusEnum.RESERVED.name().equals(inst.getInstanceStatus())
                        || InstanceStatusEnum.IN_USE.name().equals(inst.getInstanceStatus())
                        || InstanceStatusEnum.AVAILABLE.name().equals(inst.getInstanceStatus()))) {
                    targetId = inst.getId();
                    break;
                }
                if (InstanceStatusEnum.AVAILABLE.name().equals(toStatus)
                        && InstanceStatusEnum.RESERVED.name().equals(inst.getInstanceStatus())) {
                    targetId = inst.getId();
                    break;
                }
            }
        }
        if (targetId == null || !instances.containsKey(targetId)) {
            return 0;
        }
        MaterialInstanceDO inst = instances.get(targetId);
        java.util.regex.Matcher statusMatcher = java.util.regex.Pattern
                .compile("instance_status\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(segment);
        if (statusMatcher.find()) {
            Object expectedStatus = pairs.get(statusMatcher.group(1));
            if (expectedStatus != null
                    && !String.valueOf(expectedStatus).equals(inst.getInstanceStatus())) {
                return 0;
            }
        }
        if (sets.containsKey("instance_status")) {
            inst.setInstanceStatus(String.valueOf(sets.get("instance_status")));
        }
        if (sets.containsKey("reservation_id")) {
            Object v = sets.get("reservation_id");
            inst.setReservationId(v == null ? null : String.valueOf(v));
        }
        if (sets.containsKey("reserved_by_execution_id")) {
            Object v = sets.get("reserved_by_execution_id");
            inst.setReservedByExecutionId(v == null ? null : String.valueOf(v));
        }
        if (sets.containsKey("reserved_until")) {
            Object v = sets.get("reserved_until");
            inst.setReservedUntil(v instanceof LocalDateTime ldt ? ldt : null);
        }
        if (sets.containsKey("current_count")) {
            Object v = sets.get("current_count");
            inst.setCurrentCount(v == null ? null : Integer.valueOf(String.valueOf(v)));
        }
        return 1;
    }

    private Map<String, Object> resolveSetMap(String sqlSet, Map<String, Object> pairs) {
        Map<String, Object> out = new LinkedHashMap<>();
        String[] parts = sqlSet.split(",");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String col = kv[0].trim().replace("`", "");
            String expr = kv[1].trim();
            if ("null".equalsIgnoreCase(expr)) {
                out.put(col, null);
                continue;
            }
            int start = expr.indexOf("MPGENVAL");
            if (start >= 0) {
                int end = start;
                while (end < expr.length() && Character.isLetterOrDigit(expr.charAt(end))) {
                    end++;
                }
                String key = expr.substring(start, end);
                out.put(col, pairs.get(key));
            }
        }
        return out;
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
            out.sort(java.util.Comparator.comparingInt((MaterialMovementDO mv) -> {
                String key = mv.getIdempotentKey();
                int hash = key.lastIndexOf('#');
                try {
                    return Integer.parseInt(key.substring(hash + 1));
                } catch (Exception e) {
                    return 0;
                }
            }));
            return out;
        }
    }
}
