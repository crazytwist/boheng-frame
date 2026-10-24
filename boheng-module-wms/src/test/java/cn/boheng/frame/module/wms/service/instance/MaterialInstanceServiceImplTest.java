package cn.boheng.frame.module.wms.service.instance;

import cn.boheng.frame.framework.common.exception.ServiceException;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.controller.admin.movement.vo.MaterialMovementPageReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.dal.dataobject.movement.MaterialMovementDO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;
import cn.boheng.frame.module.wms.dal.mysql.instance.MaterialInstanceMapper;
import cn.boheng.frame.module.wms.dal.mysql.slot.SlotMapper;
import cn.boheng.frame.module.wms.enums.InstanceStatusEnum;
import cn.boheng.frame.module.wms.enums.MovementTypeEnum;
import cn.boheng.frame.module.wms.enums.SlotStatusEnum;
import cn.boheng.frame.module.wms.service.movement.MaterialMovementService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.MATERIAL_INSTANCE_DELETE_CHILDREN_OUTSIDE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 批量删除的两个关键不变量测试（不启动 Spring，Proxy 桩 Mapper + 手写流水替身，
 * 与 flow 模块测试同款做法）：
 *
 * <p>① 子树闭合：只删父不删子（含隔代遗漏）必须拒绝，整树或单叶允许；
 * ② 删除流水口径：只为「被删除森林的树根」记 DELETE 流水，整板删除不刷孔位流水。</p>
 */
class MaterialInstanceServiceImplTest {

    /**
     * selectList 的应答队列：按调用顺序依次消费。
     * deleteMaterialInstanceList 的第 1 次是载入待删实例，第 2 次是闭合校验查子实例。
     */
    private Deque<List<MaterialInstanceDO>> selectListResponses;

    /**
     * deleteById 收到的编号（按调用顺序，用于断言子实例先删）
     */
    private List<Long> deletedIds;

    /**
     * recordMovement 收到的流水
     */
    private List<MaterialMovementDO> recordedMovements;

    private MaterialInstanceServiceImpl service;

    /**
     * 初始化 MyBatis-Plus 的 Lambda 列缓存。正常运行由 Spring 装配 Mapper 时完成，
     * 纯单测里 LambdaUpdateWrapper / LambdaQueryWrapperX 解析 MaterialInstanceDO::getXxx
     * 依赖这份缓存，必须手动注册两个实体的 TableInfo。
     */
    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MaterialInstanceDO.class);
        TableInfoHelper.initTableInfo(assistant, SlotDO.class);
    }

    @BeforeEach
    void setUp() {
        selectListResponses = new ArrayDeque<>();
        deletedIds = new ArrayList<>();
        recordedMovements = new ArrayList<>();

        service = new MaterialInstanceServiceImpl();
        ReflectionTestUtils.setField(service, "materialInstanceMapper", instanceMapper());
        ReflectionTestUtils.setField(service, "slotMapper", slotMapper());
        ReflectionTestUtils.setField(service, "materialMovementService", new MovementDouble());
    }

    // ==================== 子树闭合校验 ====================

    /**
     * 场景一：只删父实例、子实例遗漏 → 拒绝，错误码指向子树不闭合
     */
    @Test
    void deleteParentWithoutChildIsRejected() {
        selectListResponses.add(List.of(instance(2L, 1L, "/1/2", null, InstanceStatusEnum.AVAILABLE.name())));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.validateDeleteClosure(
                List.of(instance(1L, null, "/1", 10L, InstanceStatusEnum.AVAILABLE.name()))));
        assertEquals(MATERIAL_INSTANCE_DELETE_CHILDREN_OUTSIDE.getCode(), ex.getCode());
    }

    /**
     * 场景二：父、子一起删但孙实例遗漏 → 同样拒绝（闭合校验按层识别，隔代遗漏逃不掉）
     */
    @Test
    void missedGrandchildIsRejected() {
        selectListResponses.add(List.of(
                instance(2L, 1L, "/1/2", null, InstanceStatusEnum.AVAILABLE.name()),
                instance(3L, 2L, "/1/2/3", null, InstanceStatusEnum.AVAILABLE.name())));

        ServiceException ex = assertThrows(ServiceException.class, () -> service.validateDeleteClosure(List.of(
                instance(1L, null, "/1", 10L, InstanceStatusEnum.AVAILABLE.name()),
                instance(2L, 1L, "/1/2", null, InstanceStatusEnum.AVAILABLE.name()))));
        assertEquals(MATERIAL_INSTANCE_DELETE_CHILDREN_OUTSIDE.getCode(), ex.getCode());
    }

    /**
     * 场景三：整棵树全部选中 → 允许
     */
    @Test
    void wholeTreeIsAllowed() {
        selectListResponses.add(List.of(
                instance(2L, 1L, "/1/2", null, InstanceStatusEnum.AVAILABLE.name()),
                instance(3L, 1L, "/1/3", null, InstanceStatusEnum.AVAILABLE.name())));

        assertDoesNotThrow(() -> service.validateDeleteClosure(List.of(
                instance(1L, null, "/1", 10L, InstanceStatusEnum.AVAILABLE.name()),
                instance(2L, 1L, "/1/2", null, InstanceStatusEnum.AVAILABLE.name()),
                instance(3L, 1L, "/1/3", null, InstanceStatusEnum.AVAILABLE.name()))));
    }

    /**
     * 场景四：只删单个叶子实例（父不在删除集合内）→ 允许，叶子无子实例
     */
    @Test
    void singleLeafIsAllowed() {
        selectListResponses.add(List.of());

        assertDoesNotThrow(() -> service.validateDeleteClosure(
                List.of(instance(2L, 1L, "/1/2", null, InstanceStatusEnum.AVAILABLE.name()))));
    }

    // ==================== 删除流水口径 ====================

    /**
     * 整板删除（板 + 全部孔位）：只有板（森林树根）记一条 DELETE 流水，
     * 流水带删除时的槽位与删除前状态；孔位不单独记；删除顺序子实例在前
     */
    @Test
    void rackDeleteJournalsOnlyForestRoot() {
        service.deleteInstancesWithJournal(List.of(
                instance(1L, null, "/1", 10L, InstanceStatusEnum.AVAILABLE.name()),
                instance(2L, 1L, "/1/2", null, InstanceStatusEnum.AVAILABLE.name()),
                instance(3L, 1L, "/1/3", null, InstanceStatusEnum.IN_USE.name())), "op-1");

        assertEquals(1, recordedMovements.size());
        MaterialMovementDO movement = recordedMovements.get(0);
        assertEquals(MovementTypeEnum.DELETE.getType(), movement.getMovementType());
        assertEquals(1L, movement.getInstanceId());
        assertEquals(10L, movement.getFromSlotId());
        assertEquals(InstanceStatusEnum.AVAILABLE.name(), movement.getBeforeStatus());
        // afterStatus 留空：实例已随删除离开系统，没有后续状态
        assertNull(movement.getAfterStatus());
        assertEquals("op-1", movement.getOperationId());
        assertEquals(3, deletedIds.size());
        // instance_path 深度降序：两个孔位先删，板最后删
        assertEquals(1L, deletedIds.get(deletedIds.size() - 1));
        assertTrue(deletedIds.containsAll(Arrays.asList(2L, 3L)));
    }

    /**
     * 单独删除一个子实例：父不在删除集合内，该子实例自己是树根，同样记流水（无槽位字段）
     */
    @Test
    void loneChildDeleteIsJournaled() {
        service.deleteInstancesWithJournal(
                List.of(instance(2L, 1L, "/1/2", null, InstanceStatusEnum.IN_USE.name())), null);

        assertEquals(1, recordedMovements.size());
        MaterialMovementDO movement = recordedMovements.get(0);
        assertEquals(MovementTypeEnum.DELETE.getType(), movement.getMovementType());
        assertEquals(2L, movement.getInstanceId());
        assertNull(movement.getFromSlotId());
        assertNull(movement.getOperationId());
        assertEquals(List.of(2L), deletedIds);
    }

    /**
     * 批量删除入口端到端：载入实例 → 闭合校验通过 → 树记一条流水并共享 operationId
     */
    @Test
    void deleteListJournalsForestRootEndToEnd() {
        // 第 1 次 selectList：载入待删实例（板 + 两个孔位）
        selectListResponses.add(List.of(
                instance(1L, null, "/1", 10L, InstanceStatusEnum.AVAILABLE.name()),
                instance(2L, 1L, "/1/2", null, InstanceStatusEnum.AVAILABLE.name()),
                instance(3L, 1L, "/1/3", null, InstanceStatusEnum.AVAILABLE.name())));
        // 第 2 次 selectList：闭合校验查到的子实例（都在删除集合内）
        selectListResponses.add(List.of(
                instance(2L, 1L, "/1/2", null, InstanceStatusEnum.AVAILABLE.name()),
                instance(3L, 1L, "/1/3", null, InstanceStatusEnum.AVAILABLE.name())));

        service.deleteMaterialInstanceList(List.of(1L, 2L, 3L));

        assertEquals(1, recordedMovements.size());
        assertEquals(1L, recordedMovements.get(0).getInstanceId());
        // 入口自动生成共享批次号
        assertTrue(recordedMovements.get(0).getOperationId() != null
                && !recordedMovements.get(0).getOperationId().isEmpty());
        assertEquals(3, deletedIds.size());
    }

    // ==================== 替身与工具 ====================

    private MaterialInstanceMapper instanceMapper() {
        return proxy(MaterialInstanceMapper.class, (method, args) -> {
            switch (method.getName()) {
                case "selectList" -> {
                    return new ArrayList<>(selectListResponses.isEmpty()
                            ? List.of() : selectListResponses.poll());
                }
                case "deleteById" -> {
                    deletedIds.add((Long) args[0]);
                    return 1;
                }
                // refreshSlotOccupancy 的占用数反查：删除后恒为 0（槽位释放路径）
                case "selectCount" -> {
                    return 0L;
                }
                default -> {
                    return defaultValue(method.getReturnType());
                }
            }
        });
    }

    private SlotMapper slotMapper() {
        return proxy(SlotMapper.class, (method, args) -> {
            switch (method.getName()) {
                case "selectById" -> {
                    SlotDO slot = new SlotDO();
                    slot.setId((Long) args[0]);
                    slot.setSlotCode("Z1_R1C1");
                    slot.setSlotStatus(SlotStatusEnum.OCCUPIED.name());
                    return slot;
                }
                case "update" -> {
                    return 1;
                }
                default -> {
                    return defaultValue(method.getReturnType());
                }
            }
        });
    }

    /**
     * 流水服务替身：只关心 recordMovement 的捕获，查询能力用不到
     */
    private final class MovementDouble implements MaterialMovementService {

        @Override
        public Long recordMovement(MaterialMovementDO movement) {
            recordedMovements.add(movement);
            return (long) recordedMovements.size();
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
    }

    /**
     * 构造一个实例替身
     */
    private static MaterialInstanceDO instance(long id, Long parentInstanceId,
                                               String instancePath, Long rootSlotId, String status) {
        MaterialInstanceDO inst = new MaterialInstanceDO();
        inst.setId(id);
        inst.setInstanceCode("INST-" + id);
        inst.setParentInstanceId(parentInstanceId);
        inst.setInstancePath(instancePath);
        inst.setRootInstanceId(parentInstanceId == null ? id : 1L);
        inst.setRootSlotId(rootSlotId);
        inst.setRootSlotCode(rootSlotId == null ? null : "Z1_R1C1");
        inst.setInstanceStatus(status);
        return inst;
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
}
