package cn.boheng.frame.module.wms.service.instance;

import cn.boheng.frame.framework.common.exception.ServiceException;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeReservationReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceReleaseByExecutionReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceReleaseByExecutionRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceReserveReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceReserveRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceUseReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceUseReleaseRespVO;
import cn.boheng.frame.module.wms.controller.admin.movement.vo.MaterialMovementPageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.dal.dataobject.movement.MaterialMovementDO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;
import cn.boheng.frame.module.wms.dal.mysql.instance.MaterialInstanceMapper;
import cn.boheng.frame.module.wms.dal.mysql.slot.SlotMapper;
import cn.boheng.frame.module.wms.enums.ErrorCodeConstants;
import cn.boheng.frame.module.wms.enums.InstanceStatusEnum;
import cn.boheng.frame.module.wms.enums.MovementTypeEnum;
import cn.boheng.frame.module.wms.service.movement.MaterialMovementService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * W-2.2：预留归属、按预留消耗、按执行释放、过期释放。
 */
class MaterialInstanceReservationTest {

    private final Map<Long, MaterialInstanceDO> instances = new LinkedHashMap<>();
    private final List<MaterialMovementDO> store = new ArrayList<>();
    private final AtomicLong movementIdSeq = new AtomicLong(5000);
    private MaterialInstanceServiceImpl service;

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
        service = new MaterialInstanceServiceImpl();
        ReflectionTestUtils.setField(service, "materialInstanceMapper", instanceMapper());
        ReflectionTestUtils.setField(service, "slotMapper", unusedMapper(SlotMapper.class));
        ReflectionTestUtils.setField(service, "materialMovementService", new RecordingMovementService());
    }

    @Test
    void reserveThenUseWithOwnership() {
        putAvailable(1L, "R-1");
        putAvailable(2L, "R-2");

        MaterialInstanceReserveReqVO req = new MaterialInstanceReserveReqVO();
        req.setExecutionId("EXE-1");
        req.setInstanceIds(List.of(1L, 2L));
        req.setReservedUntil(LocalDateTime.now().plusHours(1));
        req.setIdempotentKey("reserve-batch-1");

        MaterialInstanceReserveRespVO resp = service.reserve(req);
        assertFalse(resp.getIdempotentReplay());
        assertNotNull(resp.getReservationId());
        assertEquals(2, resp.getInstanceIds().size());
        assertEquals(InstanceStatusEnum.RESERVED.name(), instances.get(1L).getInstanceStatus());
        assertEquals(resp.getReservationId(), instances.get(1L).getReservationId());
        assertEquals("EXE-1", instances.get(1L).getReservedByExecutionId());
        assertEquals(2, store.size());
        assertEquals(MovementTypeEnum.RESERVE.getType(), store.get(0).getMovementType());

        MaterialInstanceReserveRespVO replay = service.reserve(req);
        assertTrue(replay.getIdempotentReplay());
        assertEquals(resp.getReservationId(), replay.getReservationId());
        assertEquals(2, store.size());

        MaterialInstanceUseReqVO useReq = new MaterialInstanceUseReqVO();
        useReq.setInstanceId(1L);
        useReq.setReservationId(resp.getReservationId());
        useReq.setExecutionId("EXE-1");
        useReq.setIdempotentKey("use-owned-1");
        MaterialInstanceUseReleaseRespVO useResp = service.use(useReq);
        assertEquals(InstanceStatusEnum.IN_USE.name(), useResp.getAfterStatus());
        assertEquals(resp.getReservationId(), instances.get(1L).getReservationId()); // 保留归属
    }

    @Test
    void useOwnedWithoutOwnerRejected() {
        MaterialInstanceDO inst = putAvailable(3L, "R-3");
        inst.setInstanceStatus(InstanceStatusEnum.RESERVED.name());
        inst.setReservationId("RSV-X");
        inst.setReservedByExecutionId("EXE-X");
        inst.setReservedUntil(LocalDateTime.now().plusHours(1));

        MaterialInstanceUseReqVO req = new MaterialInstanceUseReqVO();
        req.setInstanceId(3L);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.use(req));
        assertEquals(ErrorCodeConstants.INSTANCE_RESERVATION_OWNER_MISMATCH.getCode(), ex.getCode());
    }

    @Test
    void plainConsumeRejectsOwnedInstance() {
        MaterialInstanceDO inst = putAvailable(4L, "R-4");
        inst.setInstanceStatus(InstanceStatusEnum.RESERVED.name());
        inst.setReservationId("RSV-Y");
        inst.setReservedByExecutionId("EXE-Y");
        inst.setReservedUntil(LocalDateTime.now().plusHours(1));

        MaterialInstanceConsumeReqVO req = new MaterialInstanceConsumeReqVO();
        MaterialInstanceConsumeReqVO.Item item = new MaterialInstanceConsumeReqVO.Item();
        item.setInstanceId(4L);
        req.setItems(List.of(item));
        req.setIdempotentKey("consume-owned-illegal");
        ServiceException ex = assertThrows(ServiceException.class, () -> service.consume(req));
        assertEquals(ErrorCodeConstants.INSTANCE_RESERVATION_OWNER_MISMATCH.getCode(), ex.getCode());
    }

    @Test
    void consumeReservationWholeItemClearsOwnership() {
        MaterialInstanceDO inst = putAvailable(5L, "R-5");
        inst.setInstanceStatus(InstanceStatusEnum.RESERVED.name());
        inst.setReservationId("RSV-5");
        inst.setReservedByExecutionId("EXE-5");
        inst.setReservedUntil(LocalDateTime.now().plusHours(1));
        inst.setCurrentCount(1);

        MaterialInstanceConsumeReservationReqVO req = new MaterialInstanceConsumeReservationReqVO();
        req.setReservationId("RSV-5");
        req.setExecutionId("EXE-5");
        req.setIdempotentKey("consume-rsv-5");
        MaterialInstanceConsumeReqVO.Item item = new MaterialInstanceConsumeReqVO.Item();
        item.setInstanceId(5L);
        req.setItems(List.of(item));

        MaterialInstanceConsumeRespVO resp = service.consumeReservation(req);
        assertFalse(resp.getIdempotentReplay());
        assertEquals(InstanceStatusEnum.USED.name(), instances.get(5L).getInstanceStatus());
        assertNull(instances.get(5L).getReservationId());
    }

    @Test
    void releaseByExecutionOnlyReleasesReserved() {
        MaterialInstanceDO r = putAvailable(6L, "R-6");
        r.setInstanceStatus(InstanceStatusEnum.RESERVED.name());
        r.setReservationId("RSV-6");
        r.setReservedByExecutionId("EXE-6");
        r.setReservedUntil(LocalDateTime.now().plusHours(1));

        MaterialInstanceDO u = putAvailable(7L, "R-7");
        u.setInstanceStatus(InstanceStatusEnum.IN_USE.name());
        u.setReservationId("RSV-6");
        u.setReservedByExecutionId("EXE-6");
        u.setReservedUntil(LocalDateTime.now().plusHours(1));

        MaterialInstanceReleaseByExecutionReqVO req = new MaterialInstanceReleaseByExecutionReqVO();
        req.setExecutionId("EXE-6");
        req.setReason("cancel");
        req.setIdempotentKey("rel-exe-6");

        MaterialInstanceReleaseByExecutionRespVO resp = service.releaseByExecution(req);
        assertEquals(1, resp.getReleasedInstanceIds().size());
        assertEquals(6L, resp.getReleasedInstanceIds().get(0));
        assertEquals(1, resp.getPendingInUse().size());
        assertEquals(InstanceStatusEnum.AVAILABLE.name(), instances.get(6L).getInstanceStatus());
        assertNull(instances.get(6L).getReservationId());
        assertEquals(InstanceStatusEnum.IN_USE.name(), instances.get(7L).getInstanceStatus());
    }

    @Test
    void expireReservationsReleasesDueReserved() {
        MaterialInstanceDO r = putAvailable(8L, "R-8");
        r.setInstanceStatus(InstanceStatusEnum.RESERVED.name());
        r.setReservationId("RSV-8");
        r.setReservedByExecutionId("EXE-8");
        r.setReservedUntil(LocalDateTime.now().minusMinutes(1));

        int n = service.expireReservations(LocalDateTime.now(), 10);
        assertEquals(1, n);
        assertEquals(InstanceStatusEnum.AVAILABLE.name(), instances.get(8L).getInstanceStatus());
        assertNull(instances.get(8L).getReservationId());
        assertEquals(MovementTypeEnum.RELEASE.getType(), store.get(0).getMovementType());

        int again = service.expireReservations(LocalDateTime.now(), 10);
        assertEquals(0, again);
    }

    private MaterialInstanceDO putAvailable(long id, String code) {
        MaterialInstanceDO inst = new MaterialInstanceDO();
        inst.setId(id);
        inst.setInstanceCode(code);
        inst.setInstanceStatus(InstanceStatusEnum.AVAILABLE.name());
        inst.setCurrentCount(1);
        inst.setRootInstanceId(id);
        instances.put(id, inst);
        return inst;
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

        // 从 WHERE 段定位 id：(... id = #{ew.paramNameValuePairs.MPGENVALx} ...)
        Long targetId = null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("id\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(segment);
        if (m.find()) {
            Object v = pairs.get(m.group(1));
            if (v instanceof Long l) {
                targetId = l;
            } else if (v != null) {
                try { targetId = Long.valueOf(String.valueOf(v)); } catch (Exception ignored) {}
            }
        }
        if (targetId == null) {
            // 退化：按目标状态挑一个仍匹配前置态的实例
            String toStatus = sets.get("instance_status") == null ? null : String.valueOf(sets.get("instance_status"));
            for (MaterialInstanceDO inst : instances.values()) {
                if (InstanceStatusEnum.RESERVED.name().equals(toStatus)
                        && InstanceStatusEnum.AVAILABLE.name().equals(inst.getInstanceStatus())
                        && inst.getReservationId() == null) {
                    targetId = inst.getId();
                    break;
                }
                if (InstanceStatusEnum.IN_USE.name().equals(toStatus)
                        && InstanceStatusEnum.RESERVED.name().equals(inst.getInstanceStatus())) {
                    targetId = inst.getId();
                    break;
                }
                if (InstanceStatusEnum.AVAILABLE.name().equals(toStatus)
                        && InstanceStatusEnum.RESERVED.name().equals(inst.getInstanceStatus())) {
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
            }
        }
        if (targetId == null || !instances.containsKey(targetId)) {
            return 0;
        }
        MaterialInstanceDO inst = instances.get(targetId);
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
        // sqlSet 形如: instance_status=#{ew.paramNameValuePairs.MPGENVAL1},reservation_id=#{...}
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
            List<MaterialMovementDO> out = new ArrayList<>();
            for (MaterialMovementDO row : store) {
                if (instanceId.equals(row.getInstanceId())) {
                    out.add(row);
                }
            }
            return out;
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
            out.sort(java.util.Comparator.comparingInt((MaterialMovementDO m) -> {
                String key = m.getIdempotentKey();
                int idx = key.lastIndexOf('#');
                try {
                    return Integer.parseInt(key.substring(idx + 1));
                } catch (Exception e) {
                    return Integer.MAX_VALUE;
                }
            }));
            return out;
        }
    }
}
