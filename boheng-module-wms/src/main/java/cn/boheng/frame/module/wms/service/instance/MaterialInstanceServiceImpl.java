package cn.boheng.frame.module.wms.service.instance;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeByContainerReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstancePageReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstancePutInReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceSaveReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceTakeOutReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceTransferReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MovementTraceBaseReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.dal.dataobject.movement.MaterialMovementDO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;
import cn.boheng.frame.module.wms.dal.mysql.instance.MaterialInstanceMapper;
import cn.boheng.frame.module.wms.dal.mysql.slot.SlotMapper;
import cn.boheng.frame.module.wms.service.movement.MaterialMovementService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.*;

/**
 * 物料实例 Service 实现类
 *
 * <h3>流水一致性约束（重要）</h3>
 * 上架 / 下架 / 转移 / 消耗都必须「改实例」与「写流水」在**同一事务**内完成。
 * 因此本类不允许任何绕过流水直接改 {@code root_slot_id} 的入口：
 * {@link #updateMaterialInstance} 会显式拒绝改动槽位字段。
 *
 * @author yinan
 */
@Service
@Validated
public class MaterialInstanceServiceImpl implements MaterialInstanceService {

    /**
     * 单次批量创建的最大条数（防止一次性生成过多实例）
     */
    private static final int MAX_BATCH_SIZE = 500;

    /**
     * 槽位使用状态：占用 / 空闲
     */
    private static final String SLOT_STATUS_OCCUPIED = "OCCUPIED";
    private static final String SLOT_STATUS_FREE = "FREE";

    /**
     * 实例状态
     */
    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_IN_USE = "IN_USE";
    private static final String STATUS_USED = "USED";

    /**
     * 允许被消耗的实例状态（终态 USED / EXPIRED / DISCARDED 不可再消耗）
     */
    private static final Set<String> CONSUMABLE_STATUS = Set.of(STATUS_AVAILABLE, "RESERVED", STATUS_IN_USE);

    /**
     * 按容器消耗时的挑选策略
     */
    private static final String STRATEGY_POSITION = "POSITION";

    /**
     * 流水类型
     */
    private static final String MT_CREATE = "CREATE";
    private static final String MT_PUT_IN = "PUT_IN";
    private static final String MT_TAKE_OUT = "TAKE_OUT";
    private static final String MT_MOVE = "MOVE";
    private static final String MT_CONSUME = "CONSUME";

    @Resource
    private MaterialInstanceMapper materialInstanceMapper;

    @Resource
    private SlotMapper slotMapper;

    @Resource
    private MaterialMovementService materialMovementService;


    // ==================== 创建 / 更新 / 删除 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMaterialInstance(MaterialInstanceSaveReqVO createReqVO) {
        return doCreateMaterialInstance(createReqVO, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createMaterialInstanceBatch(List<MaterialInstanceSaveReqVO> createReqVOs) {
        // 1. 校验数量
        if (CollUtil.isEmpty(createReqVOs)) {
            throw exception(MATERIAL_INSTANCE_BATCH_EMPTY);
        }
        if (createReqVOs.size() > MAX_BATCH_SIZE) {
            throw exception(MATERIAL_INSTANCE_BATCH_SIZE_LIMIT, MAX_BATCH_SIZE);
        }
        // 2. 校验批次内编码不重复（同批次内重码，提前兜住，避免跑到 DB 逐条比对）
        Set<String> codes = new HashSet<>(createReqVOs.size());
        for (MaterialInstanceSaveReqVO reqVO : createReqVOs) {
            if (StrUtil.isNotBlank(reqVO.getInstanceCode()) && !codes.add(reqVO.getInstanceCode())) {
                throw exception(MATERIAL_INSTANCE_CODE_DUPLICATE, reqVO.getInstanceCode());
            }
        }
        // 3. 逐条插入，复用单条创建逻辑，保证物化路径（instance_path / root_instance_id）一致；
        //    同一次批量的流水共享一个 operationId，便于在流水页还原「一次操作」
        String operationId = IdUtil.fastSimpleUUID();
        List<Long> ids = new ArrayList<>(createReqVOs.size());
        for (MaterialInstanceSaveReqVO reqVO : createReqVOs) {
            ids.add(doCreateMaterialInstance(reqVO, operationId, null));
        }
        return ids;
    }

    /**
     * 创建实例的实际逻辑
     *
     * @param operationId 批量场景下透传的操作批次号，单条创建时为空（由后端生成）
     * @param movementId  非空时表示本次创建由别处发起且已归属某条流水，不再单独记 CREATE
     */
    private Long doCreateMaterialInstance(MaterialInstanceSaveReqVO createReqVO, String operationId, Long movementId) {
        // 校验实例编码的唯一性
        validateInstanceCodeUnique(createReqVO.getId(), createReqVO.getInstanceCode());

        // 插入
        MaterialInstanceDO materialInstance = BeanUtils.toBean(createReqVO, MaterialInstanceDO.class);

        // 预生成主键（雪花），用于计算物化路径
        Long id = IdWorker.getId();
        materialInstance.setId(id);

        // 计算树的冗余字段：instance_path / root_instance_id（路径依赖主键，故由后端统一计算）
        Long parentInstanceId = materialInstance.getParentInstanceId();
        if (parentInstanceId == null) {
            // 顶层实例：根指向自身
            materialInstance.setRootInstanceId(id);
            materialInstance.setInstancePath("/" + id);
        } else {
            MaterialInstanceDO parent = materialInstanceMapper.selectById(parentInstanceId);
            if (parent == null) {
                throw exception(MATERIAL_INSTANCE_PARENT_NOT_EXISTS, parentInstanceId);
            }
            materialInstance.setRootInstanceId(parent.getRootInstanceId());
            materialInstance.setInstancePath(parent.getInstancePath() + "/" + id);
        }

        // 落位校验（槽位存在 / 未停用 / 未被占用），并回填槽位编码
        SlotDO slot = prepareSlotPlacement(materialInstance);

        try {
            materialInstanceMapper.insert(materialInstance);
        } catch (DuplicateKeyException e) {
            // 并发下由唯一索引 uk_root_slot 兜底，转为友好提示
            if (StrUtil.containsIgnoreCase(e.getMessage(), "uk_root_slot")) {
                throw exception(SLOT_ALREADY_OCCUPIED, materialInstance.getRootSlotCode());
            }
            throw e;
        }

        // 记建账流水。只为顶层实例记 —— 子实例（如 96 孔板的孔位）由父实例的 CREATE 流水代表，
        // 否则一次建板会刷出 97 条流水，且孔位不会独立移动，记了也没有查询价值。
        if (movementId == null && materialInstance.getParentInstanceId() == null) {
            MaterialMovementDO movement = buildMovement(MT_CREATE, materialInstance, null, slot);
            movement.setOperationId(operationId);
            movement.setAfterStatus(materialInstance.getInstanceStatus());
            materialMovementService.recordMovement(movement);
        }

        // 同步槽位占用状态（occupied_qty / slot_status / occupied_time）
        refreshSlotOccupancy(materialInstance.getRootSlotId());
        return materialInstance.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMaterialInstance(MaterialInstanceSaveReqVO updateReqVO) {
        // 校验存在（并取出原记录）
        MaterialInstanceDO oldInstance = validateMaterialInstanceExists(updateReqVO.getId());
        // 校验实例编码的唯一性
        validateInstanceCodeUnique(updateReqVO.getId(), updateReqVO.getInstanceCode());

        // 落位不允许走更新接口：否则会绕过物料流水，导致「位置变了但没记录」。
        // 传了与原值相同的槽位视为前端回填整个对象，静默忽略即可；传了不同的则明确报错。
        if (!Objects.equals(oldInstance.getRootSlotId(), updateReqVO.getRootSlotId())) {
            throw exception(MATERIAL_INSTANCE_SLOT_ONLY_VIA_OPERATION);
        }

        MaterialInstanceDO updateObj = BeanUtils.toBean(updateReqVO, MaterialInstanceDO.class);
        // 落位相关字段一律以库中现值为准，避免被请求体覆盖
        updateObj.setRootSlotId(oldInstance.getRootSlotId());
        updateObj.setRootSlotCode(oldInstance.getRootSlotCode());
        // 树的冗余字段不接受前端传入，保持原值
        updateObj.setParentInstanceId(oldInstance.getParentInstanceId());
        updateObj.setInstancePath(oldInstance.getInstancePath());
        updateObj.setRootInstanceId(oldInstance.getRootInstanceId());

        materialInstanceMapper.updateById(updateObj);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterialInstance(Long id) {
        // 校验存在
        MaterialInstanceDO instance = validateMaterialInstanceExists(id);
        // 校验无子实例（否则物化路径断裂，子树悬空）
        Long childCount = materialInstanceMapper.selectCount(MaterialInstanceDO::getParentInstanceId, id);
        if (childCount != null && childCount > 0) {
            throw exception(MATERIAL_INSTANCE_HAS_CHILDREN, id);
        }
        // 删除
        materialInstanceMapper.deleteById(id);
        // 释放其占用的槽位
        refreshSlotOccupancy(instance.getRootSlotId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterialInstanceList(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        // 先取出待删除的实例，删除后释放它们占用的槽位
        List<MaterialInstanceDO> instances = materialInstanceMapper.selectList(
                new LambdaQueryWrapperX<MaterialInstanceDO>().in(MaterialInstanceDO::getId, ids));
        materialInstanceMapper.deleteBatch(MaterialInstanceDO::getId, ids);
        instances.stream()
                .map(MaterialInstanceDO::getRootSlotId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::refreshSlotOccupancy);
    }


    // ==================== 上架 / 下架 / 转移 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long putIn(MaterialInstancePutInReqVO reqVO) {
        MaterialInstanceDO instance = validateMaterialInstanceExists(reqVO.getInstanceId());
        // 仅顶层实例可落位
        if (instance.getParentInstanceId() != null) {
            throw exception(MATERIAL_INSTANCE_SLOT_ONLY_TOP);
        }
        // 已落位时引导走转移，避免「上架」变成隐式移动
        if (instance.getRootSlotId() != null) {
            throw exception(INSTANCE_ALREADY_PLACED, instance.getInstanceCode(), instance.getRootSlotCode());
        }
        SlotDO slot = validateSlotForPlacement(reqVO.getSlotId(), instance.getId());

        // 1) 先写流水（幂等键冲突会在这里 fail-fast，整个事务回滚，不会出现「位置变了没流水」）
        MaterialMovementDO movement = buildMovement(MT_PUT_IN, instance, null, slot);
        applyTrace(movement, reqVO, reqVO.getOperationId(), 0);
        movement.setBeforeStatus(instance.getInstanceStatus());
        movement.setAfterStatus(instance.getInstanceStatus());
        Long movementId = materialMovementService.recordMovement(movement);

        // 2) 改落位
        materialInstanceMapper.update(null, new LambdaUpdateWrapper<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getId, instance.getId())
                .set(MaterialInstanceDO::getRootSlotId, slot.getId())
                .set(MaterialInstanceDO::getRootSlotCode, slot.getSlotCode()));
        // 3) 重算槽位占用
        refreshSlotOccupancy(slot.getId());
        return movementId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long takeOut(MaterialInstanceTakeOutReqVO reqVO) {
        MaterialInstanceDO instance = validateMaterialInstanceExists(reqVO.getInstanceId());
        if (instance.getRootSlotId() == null) {
            throw exception(INSTANCE_NOT_PLACED, instance.getInstanceCode());
        }
        SlotDO fromSlot = slotMapper.selectById(instance.getRootSlotId());

        // 下架不引入新状态：位置归位置、状态归状态。需要同步改状态时由调用方显式指定。
        String beforeStatus = instance.getInstanceStatus();
        String afterStatus = StrUtil.blankToDefault(reqVO.getAfterStatus(), beforeStatus);

        MaterialMovementDO movement = buildMovement(MT_TAKE_OUT, instance, fromSlot, null);
        applyTrace(movement, reqVO, reqVO.getOperationId(), 0);
        movement.setBeforeStatus(beforeStatus);
        movement.setAfterStatus(afterStatus);
        Long movementId = materialMovementService.recordMovement(movement);

        // 清空落位：MyBatis-Plus 默认忽略 null 字段，必须用 LambdaUpdateWrapper 显式 set null
        materialInstanceMapper.update(null, new LambdaUpdateWrapper<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getId, instance.getId())
                .set(MaterialInstanceDO::getRootSlotId, null)
                .set(MaterialInstanceDO::getRootSlotCode, null)
                .set(MaterialInstanceDO::getInstanceStatus, afterStatus));
        if (fromSlot != null) {
            refreshSlotOccupancy(fromSlot.getId());
        }
        return movementId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long transfer(MaterialInstanceTransferReqVO reqVO) {
        MaterialInstanceDO instance = validateMaterialInstanceExists(reqVO.getInstanceId());
        if (instance.getRootSlotId() == null) {
            throw exception(INSTANCE_NOT_PLACED, instance.getInstanceCode());
        }
        if (Objects.equals(instance.getRootSlotId(), reqVO.getTargetSlotId())) {
            throw exception(INSTANCE_TRANSFER_SAME_SLOT);
        }
        SlotDO fromSlot = slotMapper.selectById(instance.getRootSlotId());
        SlotDO toSlot = validateSlotForPlacement(reqVO.getTargetSlotId(), instance.getId());

        // 一条 MOVE 流水同时带 from / to，不拆成「下架 + 上架」两条
        MaterialMovementDO movement = buildMovement(MT_MOVE, instance, fromSlot, toSlot);
        applyTrace(movement, reqVO, reqVO.getOperationId(), 0);
        movement.setBeforeStatus(instance.getInstanceStatus());
        movement.setAfterStatus(instance.getInstanceStatus());
        Long movementId = materialMovementService.recordMovement(movement);

        materialInstanceMapper.update(null, new LambdaUpdateWrapper<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getId, instance.getId())
                .set(MaterialInstanceDO::getRootSlotId, toSlot.getId())
                .set(MaterialInstanceDO::getRootSlotCode, toSlot.getSlotCode()));
        // 源槽位释放、目标槽位占用，两边都要重算
        refreshSlotOccupancy(fromSlot == null ? null : fromSlot.getId());
        refreshSlotOccupancy(toSlot.getId());
        return movementId;
    }


    // ==================== 消耗 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> consume(MaterialInstanceConsumeReqVO reqVO) {
        // 同一次请求的多条明细共享一个 operationId，便于在流水页归组
        String operationId = StrUtil.blankToDefault(reqVO.getOperationId(), IdUtil.fastSimpleUUID());
        List<Long> movementIds = new ArrayList<>(reqVO.getItems().size());
        int index = 0;
        for (MaterialInstanceConsumeReqVO.Item item : reqVO.getItems()) {
            MaterialInstanceDO instance = validateMaterialInstanceExists(item.getInstanceId());
            movementIds.add(doConsume(instance, item.getQty(), item.getVolUl(),
                    item.getAfterStatus(), StrUtil.blankToDefault(item.getRemark(), reqVO.getRemark()),
                    reqVO, operationId, index++));
        }
        return movementIds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> consumeByContainer(MaterialInstanceConsumeByContainerReqVO reqVO) {
        MaterialInstanceDO container = validateMaterialInstanceExists(reqVO.getRootInstanceId());
        // 取全部子实例后内存里挑：单容器子实例量级很小（≤96），一次查完比多次 count 更省
        List<MaterialInstanceDO> children = materialInstanceMapper.selectList(
                new LambdaQueryWrapperX<MaterialInstanceDO>()
                        .eq(MaterialInstanceDO::getParentInstanceId, reqVO.getRootInstanceId()));
        if (CollUtil.isEmpty(children)) {
            throw exception(INSTANCE_CONSUME_NO_CHILD, container.getInstanceCode());
        }
        // 只挑可消耗状态的；默认不含 RESERVED（已被步骤预留，不能抢）
        boolean includeReserved = Boolean.TRUE.equals(reqVO.getIncludeReserved());
        List<MaterialInstanceDO> candidates = children.stream()
                .filter(c -> includeReserved
                        ? CONSUMABLE_STATUS.contains(c.getInstanceStatus())
                        : STATUS_AVAILABLE.equals(c.getInstanceStatus()))
                .sorted(positionComparator(reqVO.getStrategy()))
                .toList();
        if (candidates.size() < reqVO.getCount()) {
            throw exception(INSTANCE_CONSUME_NOT_ENOUGH_CHILD, container.getInstanceCode(),
                    reqVO.getCount(), candidates.size());
        }

        String operationId = StrUtil.blankToDefault(reqVO.getOperationId(), IdUtil.fastSimpleUUID());
        String afterStatus = StrUtil.blankToDefault(reqVO.getAfterStatus(), STATUS_USED);
        List<Long> consumedIds = new ArrayList<>(reqVO.getCount());
        int index = 0;
        for (MaterialInstanceDO child : candidates.subList(0, reqVO.getCount())) {
            // 每个被挑中的子实例整件消耗掉（一件实物 = 一条流水）
            doConsume(child, null, null, afterStatus, reqVO.getRemark(), reqVO, operationId, index++);
            consumedIds.add(child.getId());
        }
        return consumedIds;
    }

    /**
     * 消耗单个实例的实际逻辑：写一条 CONSUME 流水 + 回写实例的数量 / 体积 / 状态
     *
     * @param qty         离散消耗数量（与 volUl 二选一，volUl 优先；**两者都不传表示整件消耗**）
     * @param volUl       连续消耗体积
     * @param afterStatus 消耗后状态（为空则按剩余量推导）
     * @return 流水编号
     */
    private Long doConsume(MaterialInstanceDO instance, Integer qty, BigDecimal volUl, String afterStatus,
                           String remark, MovementTraceBaseReqVO trace, String operationId, int index) {
        // 1. 状态校验
        if (!CONSUMABLE_STATUS.contains(instance.getInstanceStatus())) {
            throw exception(INSTANCE_CONSUME_STATUS_ILLEGAL, instance.getInstanceCode(), instance.getInstanceStatus());
        }
        boolean byVolume = volUl != null && volUl.compareTo(BigDecimal.ZERO) > 0;

        // 2. 算变更前后。规则：
        //    - 传了 volUl       → 按体积扣减
        //    - 传了 qty         → 按数量扣减
        //    - 两者都不传       → 整件消耗（把该实例一次用完，after 归零）
        //    current_count 为空时视为单件实物（一期约定 1 个实例 = 1 件）
        Integer beforeQty = null;
        Integer afterQty = null;
        BigDecimal beforeVol = instance.getCurrentVolUl();
        BigDecimal afterVol = null;
        if (byVolume) {
            BigDecimal before = beforeVol == null ? BigDecimal.ZERO : beforeVol;
            if (before.compareTo(volUl) < 0) {
                throw exception(INSTANCE_CONSUME_EXCEED, instance.getInstanceCode(), volUl, before);
            }
            afterVol = before.subtract(volUl);
        } else {
            beforeQty = instance.getCurrentCount() == null ? 1 : instance.getCurrentCount();
            int consumeQty = qty == null ? beforeQty : qty;
            if (beforeQty < consumeQty) {
                throw exception(INSTANCE_CONSUME_EXCEED, instance.getInstanceCode(), consumeQty, beforeQty);
            }
            afterQty = beforeQty - consumeQty;
        }

        // 3. 状态推导：剩余量归零 → USED；仍有剩余 → IN_USE（调用方可显式覆盖）
        String beforeStatus = instance.getInstanceStatus();
        String finalStatus = StrUtil.blankToDefault(afterStatus, deriveStatusAfterConsume(byVolume, afterQty, afterVol));

        // 4. 写流水（幂等键按明细序号派生，保证一次请求内多条流水各有唯一键）
        MaterialMovementDO movement = buildMovement(MT_CONSUME, instance, null, null);
        applyTrace(movement, trace, operationId, index);
        movement.setRemark(remark);
        movement.setBeforeStatus(beforeStatus);
        movement.setAfterStatus(finalStatus);
        if (byVolume) {
            movement.setBeforeVolUl(beforeVol);
            movement.setChangeVolUl(volUl.negate());
            movement.setAfterVolUl(afterVol);
        } else {
            movement.setBeforeQty(beforeQty);
            movement.setChangeQty(afterQty - beforeQty);
            movement.setAfterQty(afterQty);
        }
        Long movementId = materialMovementService.recordMovement(movement);

        // 5. 回写实例：数量 / 体积 / 状态
        LambdaUpdateWrapper<MaterialInstanceDO> update = new LambdaUpdateWrapper<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getId, instance.getId())
                .set(MaterialInstanceDO::getInstanceStatus, finalStatus);
        if (byVolume) {
            update.set(MaterialInstanceDO::getCurrentVolUl, afterVol);
        } else {
            update.set(MaterialInstanceDO::getCurrentCount, afterQty);
        }
        materialInstanceMapper.update(null, update);
        return movementId;
    }

    /**
     * 消耗后的状态推导：剩余量归零 → USED，仍有剩余 → IN_USE
     */
    private String deriveStatusAfterConsume(boolean byVolume, Integer afterQty, BigDecimal afterVol) {
        if (byVolume) {
            return afterVol == null || afterVol.compareTo(BigDecimal.ZERO) <= 0 ? STATUS_USED : STATUS_IN_USE;
        }
        return afterQty == null || afterQty <= 0 ? STATUS_USED : STATUS_IN_USE;
    }

    /**
     * 按容器消耗时的子实例排序：POSITION 按位置码自然序（A1 < A2 < A10），其余按创建先后
     */
    private Comparator<MaterialInstanceDO> positionComparator(String strategy) {
        if (!STRATEGY_POSITION.equalsIgnoreCase(StrUtil.blankToDefault(strategy, STRATEGY_POSITION))) {
            return Comparator.comparing(MaterialInstanceDO::getId);
        }
        return Comparator
                .comparing(MaterialInstanceDO::getParentPositionCode,
                        Comparator.nullsLast(MaterialInstanceServiceImpl::comparePositionCode))
                .thenComparing(MaterialInstanceDO::getId);
    }

    /**
     * 位置码自然序比较：字母段按字典序、数字段按数值序（避免 A10 排在 A2 前面）
     */
    @VisibleForTesting
    static int comparePositionCode(String a, String b) {
        int ia = 0, ib = 0;
        while (ia < a.length() && ib < b.length()) {
            char ca = a.charAt(ia), cb = b.charAt(ib);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int sa = ia, sb = ib;
                while (ia < a.length() && Character.isDigit(a.charAt(ia))) {
                    ia++;
                }
                while (ib < b.length() && Character.isDigit(b.charAt(ib))) {
                    ib++;
                }
                int na = Integer.parseInt(a.substring(sa, ia));
                int nb = Integer.parseInt(b.substring(sb, ib));
                if (na != nb) {
                    return Integer.compare(na, nb);
                }
            } else {
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                ia++;
                ib++;
            }
        }
        return Integer.compare(a.length(), b.length());
    }


    // ==================== 流水装配 ====================

    /**
     * 装配流水的主体与位置字段
     */
    private MaterialMovementDO buildMovement(String type, MaterialInstanceDO instance, SlotDO fromSlot, SlotDO toSlot) {
        MaterialMovementDO movement = MaterialMovementDO.builder()
                .movementType(type)
                .instanceId(instance.getId())
                .instanceCode(instance.getInstanceCode())
                .rootInstanceId(instance.getRootInstanceId())
                .containerTypeCode(instance.getContainerTypeCode())
                .contentDefCode(instance.getContentDefCode())
                .contentType(instance.getContentType())
                .build();
        if (fromSlot != null) {
            movement.setFromSlotId(fromSlot.getId());
            movement.setFromSlotCode(fromSlot.getSlotCode());
            movement.setFromZoneCode(fromSlot.getZoneCode());
        }
        if (toSlot != null) {
            movement.setToSlotId(toSlot.getId());
            movement.setToSlotCode(toSlot.getSlotCode());
            movement.setToZoneCode(toSlot.getZoneCode());
        }
        return movement;
    }

    /**
     * 装配流水的追溯字段（操作人 / 来源 / 单据 / 幂等键等）。
     * operator、operateTime、bizSource、operatorType 允许为空，由 Service 兜底填充。
     */
    private void applyTrace(MaterialMovementDO movement, MovementTraceBaseReqVO trace,
                            String operationId, int index) {
        if (trace == null) {
            return;
        }
        movement.setBizSource(trace.getBizSource());
        movement.setOperatorType(trace.getOperatorType());
        movement.setRefType(trace.getRefType());
        movement.setRefId(trace.getRefId());
        movement.setRefNo(trace.getRefNo());
        movement.setOperateTime(trace.getOperateTime());
        if (StrUtil.isNotBlank(trace.getRemark())) {
            movement.setRemark(trace.getRemark());
        }
        movement.setOperationId(StrUtil.blankToDefault(operationId, trace.getOperationId()));
        // 幂等键按明细序号派生：一次请求多条流水各有唯一键，同时整批保持可重放语义
        if (StrUtil.isNotBlank(trace.getIdempotentKey())) {
            movement.setIdempotentKey(trace.getIdempotentKey() + "#" + index);
        }
    }


    // ==================== 查询 ====================

    @Override
    public MaterialInstanceDO getMaterialInstance(Long id) {
        return materialInstanceMapper.selectById(id);
    }

    @Override
    public PageResult<MaterialInstanceDO> getMaterialInstancePage(MaterialInstancePageReqVO pageReqVO) {
        return materialInstanceMapper.selectPage(pageReqVO);
    }


    @Override
    public List<MaterialInstanceDO> getMaterialInstanceList() {
        return materialInstanceMapper.selectList();
    }

    @Override
    public List<MaterialInstanceDO> getUnplacedList() {
        // 未落位的顶层实例（只有顶层才能上架）
        return materialInstanceMapper.selectList(new LambdaQueryWrapperX<MaterialInstanceDO>()
                .isNull(MaterialInstanceDO::getParentInstanceId)
                .isNull(MaterialInstanceDO::getRootSlotId)
                .orderByDesc(MaterialInstanceDO::getId));
    }


    // ==================== 校验与槽位回写 ====================

    @VisibleForTesting
    MaterialInstanceDO validateMaterialInstanceExists(Long id) {
        MaterialInstanceDO materialInstance = materialInstanceMapper.selectById(id);
        if (materialInstance == null) {
            throw exception(MATERIAL_INSTANCE_NOT_EXISTS);
        }
        return materialInstance;
    }

    /**
     * 落位前准备：校验槽位可用性（存在 / 未停用 / 未被其他实例占用），并以后端槽位编码为准回填冗余字段
     *
     * @param instance 待落位的实例（rootSlotId 可能为空，表示不落位）
     * @return 命中的槽位（未落位时返回 null）
     */
    private SlotDO prepareSlotPlacement(MaterialInstanceDO instance) {
        Long slotId = instance.getRootSlotId();
        if (slotId == null) {
            // 未落位：清空冗余槽位编码
            instance.setRootSlotCode(null);
            return null;
        }
        // 仅顶层实例可落位（子实例的位置由 parent_position_code 表达，不占槽位）
        if (instance.getParentInstanceId() != null) {
            throw exception(MATERIAL_INSTANCE_SLOT_ONLY_TOP);
        }
        SlotDO slot = validateSlotForPlacement(slotId, instance.getId());
        // 冗余编码以后端为准
        instance.setRootSlotCode(slot.getSlotCode());
        return slot;
    }

    /**
     * 校验槽位可用于落位：存在 / 未停用 / 未被其他实例占用
     *
     * @param slotId            槽位编号
     * @param excludeInstanceId 排除的实例编号（改自己时不算冲突）
     */
    private SlotDO validateSlotForPlacement(Long slotId, Long excludeInstanceId) {
        SlotDO slot = slotMapper.selectById(slotId);
        if (slot == null) {
            throw exception(SLOT_NOT_EXISTS);
        }
        // 停用槽位不可上架
        if (slot.getStatus() != null && slot.getStatus() == 1) {
            throw exception(SLOT_DISABLED, slot.getSlotCode());
        }
        // 一个槽位同一时间只允许一个实例上架
        Long occupied = materialInstanceMapper.selectCount(new LambdaQueryWrapperX<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getRootSlotId, slotId)
                .neIfPresent(MaterialInstanceDO::getId, excludeInstanceId));
        if (occupied != null && occupied > 0) {
            throw exception(SLOT_ALREADY_OCCUPIED, slot.getSlotCode());
        }
        return slot;
    }

    /**
     * 重算并同步槽位占用状态。
     * <p>
     * 以 {@code wms_material_instance.root_slot_id} 为唯一真相源反查占用数，避免增量加减造成漂移；
     * 处于 LOCKED / CHECKING 的槽位不参与自动释放，保留调度域的锁语义。
     *
     * @param slotId 槽位编号（为空则忽略）
     */
    private void refreshSlotOccupancy(Long slotId) {
        if (slotId == null) {
            return;
        }
        SlotDO slot = slotMapper.selectById(slotId);
        if (slot == null) {
            return;
        }
        Long count = materialInstanceMapper.selectCount(MaterialInstanceDO::getRootSlotId, slotId);
        int occupiedQty = count == null ? 0 : count.intValue();
        boolean occupied = occupiedQty > 0;
        // 占用：置 OCCUPIED；释放：仅从 OCCUPIED 回到 FREE，保留 LOCKED / CHECKING
        String newStatus;
        if (occupied) {
            newStatus = SLOT_STATUS_OCCUPIED;
        } else if (SLOT_STATUS_OCCUPIED.equals(slot.getSlotStatus())) {
            newStatus = SLOT_STATUS_FREE;
        } else {
            newStatus = slot.getSlotStatus();
        }
        slotMapper.update(null, new LambdaUpdateWrapper<SlotDO>()
                .eq(SlotDO::getId, slotId)
                .set(SlotDO::getOccupiedQty, occupiedQty)
                .set(SlotDO::getSlotStatus, newStatus)
                .set(SlotDO::getOccupiedTime, occupied ? LocalDateTime.now() : null));
    }


    @VisibleForTesting
    void validateInstanceCodeUnique(Long id, String instanceCode) {
        if (StrUtil.isBlank(instanceCode)) {
            return;
        }
        MaterialInstanceDO materialInstance = materialInstanceMapper.selectByInstanceCode(instanceCode);
        if (materialInstance == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的记录
        if (id == null || !materialInstance.getId().equals(id)) {
            throw exception(MATERIAL_INSTANCE_CODE_DUPLICATE, instanceCode);
        }
    }
}
