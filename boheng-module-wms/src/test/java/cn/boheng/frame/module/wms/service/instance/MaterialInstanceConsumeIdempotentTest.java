package cn.boheng.frame.module.wms.service.instance;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeRespVO;
import cn.boheng.frame.module.wms.controller.admin.movement.vo.MaterialMovementPageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.dal.dataobject.movement.MaterialMovementDO;
import cn.boheng.frame.module.wms.dal.mysql.instance.MaterialInstanceMapper;
import cn.boheng.frame.module.wms.dal.mysql.slot.SlotMapper;
import cn.boheng.frame.module.wms.enums.InstanceStatusEnum;
import cn.boheng.frame.module.wms.enums.MovementTypeEnum;
import cn.boheng.frame.module.wms.service.movement.MaterialMovementService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.MOVEMENT_IDEMPOTENT_DUPLICATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase C：消耗统一返回值 + 业务幂等键回放（D1-a）。
 * 不启动 Spring；Proxy 桩 Mapper + 内存流水替身。
 */
class MaterialInstanceConsumeIdempotentTest {

    private final Map<Long, MaterialInstanceDO> instances = new HashMap<>();
    private final List<MaterialMovementDO> store = new ArrayList<>();
    private final AtomicLong movementIdSeq = new AtomicLong(5000);

    private MaterialInstanceServiceImpl service;
    private RecordingMovementService movementService;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MaterialInstanceDO.class);
    }

    @BeforeEach
    void setUp() {
        instances.clear();
        store.clear();
        movementIdSeq.set(5000);
        service = new MaterialInstanceServiceImpl();
        movementService = new RecordingMovementService();
        ReflectionTestUtils.setField(service, "materialInstanceMapper", instanceMapper());
        ReflectionTestUtils.setField(service, "slotMapper", unusedMapper(SlotMapper.class));
        ReflectionTestUtils.setField(service, "materialMovementService", movementService);
    }

    @Test
    void consumeReturnsUnifiedRespOnFirstCall() {
        putInstance(11L, "INST-11", InstanceStatusEnum.AVAILABLE.name(), 1, null);

        MaterialInstanceConsumeReqVO req = consumeReq("biz-consume-1", 11L);
        MaterialInstanceConsumeRespVO resp = service.consume(req);

        assertFalse(Boolean.TRUE.equals(resp.getIdempotentReplay()));
        assertEquals(1, resp.getMovementIds().size());
        assertEquals(List.of(11L), resp.getConsumedInstanceIds());
        assertEquals("OP-1", resp.getOperationId());
        assertEquals(InstanceStatusEnum.USED.name(), instances.get(11L).getInstanceStatus());
        assertEquals(1, store.size());
        assertEquals("biz-consume-1#0", store.get(0).getIdempotentKey());
    }

    @Test
    void consumeReplaysWhenBusinessKeyAlreadyPresent() {
        putInstance(11L, "INST-11", InstanceStatusEnum.USED.name(), 0, null);
        MaterialMovementDO existing = MaterialMovementDO.builder()
                .id(777L)
                .movementType(MovementTypeEnum.CONSUME.getType())
                .instanceId(11L)
                .operationId("OP-FIRST")
                .idempotentKey("biz-consume-1#0")
                .build();
        store.add(existing);

        MaterialInstanceConsumeReqVO req = consumeReq("biz-consume-1", 11L);
        MaterialInstanceConsumeRespVO resp = service.consume(req);

        assertTrue(Boolean.TRUE.equals(resp.getIdempotentReplay()));
        assertEquals(List.of(777L), resp.getMovementIds());
        assertEquals(List.of(11L), resp.getConsumedInstanceIds());
        assertEquals("OP-FIRST", resp.getOperationId());
        // 回放不得再写流水、不得再改库存（已是 USED/0）
        assertEquals(1, store.size());
        assertEquals(0, instances.get(11L).getCurrentCount().intValue());
    }

    @Test
    void consumeReplaysAfterIdempotentDuplicateDuringWrite() {
        putInstance(12L, "INST-12", InstanceStatusEnum.AVAILABLE.name(), 1, null);
        // 预查 miss：store 先空；insert 时由 movementService 模拟撞键，并在抛错前写入「首次」流水
        movementService.failNextInsertWithDuplicateAndSeedReplay = true;

        MaterialInstanceConsumeReqVO req = consumeReq("biz-race-1", 12L);
        MaterialInstanceConsumeRespVO resp = service.consume(req);

        assertTrue(Boolean.TRUE.equals(resp.getIdempotentReplay()));
        assertEquals(List.of(12L), resp.getConsumedInstanceIds());
        assertEquals(1, resp.getMovementIds().size());
    }

    private static MaterialInstanceConsumeReqVO consumeReq(String key, long instanceId) {
        MaterialInstanceConsumeReqVO req = new MaterialInstanceConsumeReqVO();
        req.setIdempotentKey(key);
        req.setOperationId("OP-1");
        req.setBizSource("FLOW");
        MaterialInstanceConsumeReqVO.Item item = new MaterialInstanceConsumeReqVO.Item();
        item.setInstanceId(instanceId);
        req.setItems(List.of(item));
        return req;
    }

    private void putInstance(long id, String code, String status, Integer count, java.math.BigDecimal vol) {
        MaterialInstanceDO inst = new MaterialInstanceDO();
        inst.setId(id);
        inst.setInstanceCode(code);
        inst.setInstanceStatus(status);
        inst.setCurrentCount(count);
        inst.setCurrentVolUl(vol);
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
                        // CAS 成功：按当前内存对象已在 doConsume 前读到的状态，直接把目标写回
                        // 简化：从 wrapper 无法轻松解析，改为「若实例仍是 AVAILABLE 则改为 USED/count」——
                        // 实际 doConsume 使用 LambdaUpdateWrapper；这里对单测场景做最小成功路径：
                        Wrapper<?> wrapper = (Wrapper<?>) a[1];
                        // 找到被更新的实例：遍历，把 AVAILABLE 且 count>0 的扣成 USED
                        for (MaterialInstanceDO inst : instances.values()) {
                            if (InstanceStatusEnum.AVAILABLE.name().equals(inst.getInstanceStatus())
                                    || InstanceStatusEnum.IN_USE.name().equals(inst.getInstanceStatus())) {
                                // 整件消耗路径
                                if (inst.getCurrentCount() != null && inst.getCurrentCount() > 0) {
                                    inst.setCurrentCount(0);
                                    inst.setInstanceStatus(InstanceStatusEnum.USED.name());
                                    return 1;
                                }
                                if (inst.getCurrentCount() == null) {
                                    inst.setCurrentCount(0);
                                    inst.setInstanceStatus(InstanceStatusEnum.USED.name());
                                    return 1;
                                }
                            }
                        }
                        return 0;
                    }
                    throw new UnsupportedOperationException(name + " args=" + (a == null ? null : java.util.Arrays.toString(a)));
                });
    }

    private static <T> T unusedMapper(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type},
                (p, m, a) -> {
                    if ("toString".equals(m.getName())) {
                        return type.getSimpleName();
                    }
                    throw new UnsupportedOperationException(m.getName());
                }));
    }

    private final class RecordingMovementService implements MaterialMovementService {
        boolean failNextInsertWithDuplicateAndSeedReplay;

        @Override
        public Long recordMovement(MaterialMovementDO movement) {
            if (failNextInsertWithDuplicateAndSeedReplay) {
                failNextInsertWithDuplicateAndSeedReplay = false;
                MaterialMovementDO seeded = MaterialMovementDO.builder()
                        .id(movementIdSeq.incrementAndGet())
                        .movementType(movement.getMovementType())
                        .instanceId(movement.getInstanceId())
                        .operationId(movement.getOperationId())
                        .idempotentKey(movement.getIdempotentKey())
                        .build();
                store.add(seeded);
                throw cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil
                        .exception(MOVEMENT_IDEMPOTENT_DUPLICATE, movement.getIdempotentKey());
            }
            // 预查语义：若行键已在 store，抛 duplicate（与正式实现一致）
            if (movement.getIdempotentKey() != null) {
                for (MaterialMovementDO row : store) {
                    if (movement.getIdempotentKey().equals(row.getIdempotentKey())) {
                        throw cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil
                                .exception(MOVEMENT_IDEMPOTENT_DUPLICATE, movement.getIdempotentKey());
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
