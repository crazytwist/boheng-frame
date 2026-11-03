package cn.boheng.frame.module.wms.service.instance;

import cn.boheng.frame.framework.common.exception.ServiceException;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceCheckReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceCheckRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceReleaseReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceUseReleaseRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceUseReqVO;
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
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase D：check 结果对象 + 最小 use/release 状态闭环与幂等回放。
 */
class MaterialInstanceCheckUseReleaseTest {

    private final Map<Long, MaterialInstanceDO> instances = new LinkedHashMap<>();
    private final List<MaterialMovementDO> store = new ArrayList<>();
    private final AtomicLong movementIdSeq = new AtomicLong(1000);
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
    void checkReturnsResultObjectWhenUnavailable() {
        putInstance(1L, "A-1", InstanceStatusEnum.USED.name(), 0);
        putInstance(2L, "A-2", InstanceStatusEnum.AVAILABLE.name(), 5);

        MaterialInstanceCheckReqVO req = new MaterialInstanceCheckReqVO();
        MaterialInstanceCheckReqVO.Item i1 = new MaterialInstanceCheckReqVO.Item();
        i1.setInstanceId(1L);
        MaterialInstanceCheckReqVO.Item i2 = new MaterialInstanceCheckReqVO.Item();
        i2.setInstanceId(2L);
        i2.setMinQty(3);
        req.setItems(List.of(i1, i2));

        MaterialInstanceCheckRespVO resp = service.checkAvailability(req);
        assertFalse(resp.getOk());
        assertEquals(2, resp.getItems().size());
        assertFalse(resp.getItems().get(0).getAvailable());
        assertTrue(resp.getItems().get(1).getAvailable());
    }

    @Test
    void useThenReleaseTransitionsStatus() {
        putInstance(10L, "U-1", InstanceStatusEnum.AVAILABLE.name(), 1);

        MaterialInstanceUseReqVO useReq = new MaterialInstanceUseReqVO();
        useReq.setInstanceId(10L);
        useReq.setIdempotentKey("flow:1:n1:0:1:use");
        MaterialInstanceUseReleaseRespVO useResp = service.use(useReq);
        assertEquals(InstanceStatusEnum.AVAILABLE.name(), useResp.getBeforeStatus());
        assertEquals(InstanceStatusEnum.IN_USE.name(), useResp.getAfterStatus());
        assertEquals(InstanceStatusEnum.IN_USE.name(), instances.get(10L).getInstanceStatus());
        assertEquals(MovementTypeEnum.STATUS_CHANGE.getType(), store.get(0).getMovementType());
        assertFalse(useResp.getIdempotentReplay());

        MaterialInstanceReleaseReqVO releaseReq = new MaterialInstanceReleaseReqVO();
        releaseReq.setInstanceId(10L);
        releaseReq.setIdempotentKey("flow:1:n1:0:1:release");
        MaterialInstanceUseReleaseRespVO releaseResp = service.release(releaseReq);
        assertEquals(InstanceStatusEnum.IN_USE.name(), releaseResp.getBeforeStatus());
        assertEquals(InstanceStatusEnum.AVAILABLE.name(), releaseResp.getAfterStatus());
        assertEquals(InstanceStatusEnum.AVAILABLE.name(), instances.get(10L).getInstanceStatus());
        assertEquals(MovementTypeEnum.RELEASE.getType(), store.get(1).getMovementType());
    }

    @Test
    void useReplaysOnSameIdempotentKey() {
        putInstance(11L, "U-2", InstanceStatusEnum.AVAILABLE.name(), 1);
        MaterialInstanceUseReqVO req = new MaterialInstanceUseReqVO();
        req.setInstanceId(11L);
        req.setIdempotentKey("biz-use-1");
        MaterialInstanceUseReleaseRespVO first = service.use(req);
        MaterialInstanceUseReleaseRespVO second = service.use(req);
        assertFalse(first.getIdempotentReplay());
        assertTrue(second.getIdempotentReplay());
        assertEquals(first.getMovementId(), second.getMovementId());
        assertEquals(1, store.size());
        assertEquals(InstanceStatusEnum.IN_USE.name(), instances.get(11L).getInstanceStatus());
    }

    @Test
    void useRejectsIllegalStatus() {
        putInstance(12L, "U-3", InstanceStatusEnum.USED.name(), 0);
        MaterialInstanceUseReqVO req = new MaterialInstanceUseReqVO();
        req.setInstanceId(12L);
        ServiceException ex = assertThrows(ServiceException.class, () -> service.use(req));
        assertEquals(ErrorCodeConstants.INSTANCE_USE_STATUS_ILLEGAL.getCode(), ex.getCode());
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
                        // 单测简化：按当前状态做最小闭环跳转
                        for (MaterialInstanceDO inst : instances.values()) {
                            if (InstanceStatusEnum.AVAILABLE.name().equals(inst.getInstanceStatus())
                                    || InstanceStatusEnum.RESERVED.name().equals(inst.getInstanceStatus())) {
                                inst.setInstanceStatus(InstanceStatusEnum.IN_USE.name());
                                return 1;
                            }
                            if (InstanceStatusEnum.IN_USE.name().equals(inst.getInstanceStatus())) {
                                inst.setInstanceStatus(InstanceStatusEnum.AVAILABLE.name());
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
