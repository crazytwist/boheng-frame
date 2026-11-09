package cn.boheng.frame.module.wms.service.instance;

import cn.boheng.frame.framework.common.exception.ServiceException;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceBatchPutInReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeByContainerReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceCheckReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceCheckRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceReleaseReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceReserveReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceReserveRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeReservationReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceReleaseByExecutionReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceReleaseByExecutionRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceInstantiateReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceInstantiateRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceUseReleaseRespVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceUseReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstancePageReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstancePutInReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceSaveReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceTakeOutReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceTransferReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MovementTraceBaseReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.container.ContainerTypeDO;
import cn.boheng.frame.module.wms.dal.dataobject.content.ContentDefDO;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;
import cn.boheng.frame.module.wms.dal.dataobject.movement.MaterialMovementDO;
import cn.boheng.frame.module.wms.dal.dataobject.sample.SampleInfoDO;
import cn.boheng.frame.module.wms.dal.dataobject.slot.SlotDO;
import cn.boheng.frame.module.wms.dal.mysql.container.ContainerTypeMapper;
import cn.boheng.frame.module.wms.dal.mysql.content.ContentDefMapper;
import cn.boheng.frame.module.wms.dal.mysql.instance.MaterialInstanceMapper;
import cn.boheng.frame.module.wms.dal.mysql.sample.SampleInfoMapper;
import cn.boheng.frame.module.wms.dal.mysql.slot.SlotMapper;
import cn.boheng.frame.module.wms.enums.InstanceStatusEnum;
import cn.boheng.frame.module.wms.enums.MovementTypeEnum;
import cn.boheng.frame.module.wms.enums.SlotStatusEnum;
import cn.boheng.frame.module.wms.service.movement.MaterialMovementService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.*;

/**
 * 物料实例 Service 实现类
 *
 * <h3>流水一致性约束（重要）</h3>
 * 上架 / 下架 / 转移 / 消耗 / 删除都必须「改实例」与「写流水」在**同一事务**内完成。
 * 因此本类不允许任何绕过流水直接改 {@code root_slot_id} 的入口：
 * {@link #updateMaterialInstance} 会显式拒绝改动槽位字段；
 * 删除（含批量下架）统一经 {@link #deleteInstancesWithJournal}，删除前先记 DELETE 流水。
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
     * 按容器消耗时的挑选策略
     */
    private static final String STRATEGY_POSITION = "POSITION";

    @Resource
    private MaterialInstanceMapper materialInstanceMapper;

    @Resource
    private SlotMapper slotMapper;

    @Resource
    private SampleInfoMapper sampleInfoMapper;

    @Resource
    private MaterialMovementService materialMovementService;

    @Resource
    private ContentDefMapper contentDefMapper;

    @Resource
    private ContainerTypeMapper containerTypeMapper;


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
        return doCreateMaterialInstance(createReqVO, operationId, movementId, null, 0);
    }

    private Long doCreateMaterialInstance(MaterialInstanceSaveReqVO createReqVO, String operationId, Long movementId,
                                          MovementTraceBaseReqVO createTrace, int createIndex) {
        // 校验实例编码的唯一性
        validateInstanceCodeUnique(createReqVO.getId(), createReqVO.getInstanceCode());
        // 样本类实例：校验关联样本存在
        validateSampleIdIfPresent(createReqVO.getSampleId());

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
            MaterialMovementDO movement = buildMovement(MovementTypeEnum.CREATE.getType(), materialInstance, null, slot);
            movement.setOperationId(operationId);
            movement.setAfterStatus(materialInstance.getInstanceStatus());
            if (createTrace != null) {
                applyTrace(movement, createTrace, operationId, createIndex);
            }
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
        // 样本类实例：校验关联样本存在
        validateSampleIdIfPresent(updateReqVO.getSampleId());

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
        // 先记 DELETE 流水再删行（同一事务），保证「删了东西必有流水」
        deleteInstancesWithJournal(List.of(instance), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterialInstanceList(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        // 先取出待删除的实例（不存在的编号静默跳过，与单条删除的存在性语义保持宽松一致）
        List<MaterialInstanceDO> instances = materialInstanceMapper.selectList(
                new LambdaQueryWrapperX<MaterialInstanceDO>().in(MaterialInstanceDO::getId, ids));
        if (instances.isEmpty()) {
            return;
        }
        // 子树闭合校验：任何一个被删实例的子实例不在本次集合内，删除后子树就会悬空
        validateDeleteClosure(instances);
        // 同一次批量删除的 DELETE 流水共享一个 operationId，便于在流水页还原「一次操作」
        deleteInstancesWithJournal(instances, IdUtil.fastSimpleUUID());
    }

    /**
     * 批量删除的子树闭合校验：每个被删实例的子实例必须也在删除集合内。
     * 单独删父留子会造成 instance_path 断裂、子树悬空，这里直接拒绝并指明违规父实例。
     */
    @VisibleForTesting
    void validateDeleteClosure(List<MaterialInstanceDO> instances) {
        Set<Long> idSet = instances.stream().map(MaterialInstanceDO::getId).collect(Collectors.toSet());
        List<MaterialInstanceDO> children = materialInstanceMapper.selectList(
                new LambdaQueryWrapperX<MaterialInstanceDO>().in(MaterialInstanceDO::getParentInstanceId, idSet));
        // 子实例自身不在删除集合内的，就是会被悬空的那部分
        Map<Long, Long> outsideCountByParent = children.stream()
                .filter(child -> !idSet.contains(child.getId()))
                .collect(Collectors.groupingBy(MaterialInstanceDO::getParentInstanceId, Collectors.counting()));
        if (outsideCountByParent.isEmpty()) {
            return;
        }
        instances.stream()
                .filter(instance -> outsideCountByParent.containsKey(instance.getId()))
                .findFirst()
                .ifPresentOrElse(parent -> {
                    throw exception(MATERIAL_INSTANCE_DELETE_CHILDREN_OUTSIDE,
                            parent.getInstanceCode(), outsideCountByParent.get(parent.getId()));
                }, () -> {
                    // 理论不可达：悬空子实例的父实例必在本次集合内
                    throw exception(MATERIAL_INSTANCE_HAS_CHILDREN, outsideCountByParent.keySet().iterator().next());
                });
    }

    /**
     * 删除已通过子树校验的一组实例：先为「被删除森林的树根」各记一条 DELETE 流水，再删行、重算槽位占用。
     *
     * <p>树根 = 父实例不在本组内的实例。口径与建账流水一致：整板删除时只有顶层实例记流水
     * （子实例随父删除，不单独记，避免一次删板刷出 97 条流水）；单独删除某个子实例时，
     * 该子实例自己是树根，同样有流水可查。</p>
     *
     * <p>删除按 instance_path 深度降序（子实例先删）。当前表没有父子自引用外键，顺序本无约束；
     * 这是对未来可能加外键的防御，父先删会让子行失去引用。</p>
     *
     * @param instances   待删除实例（调用方须已完成子树闭合 / 无子实例校验）
     * @param operationId 操作批次号（单条删除传 null；批量删除传共享批次号）
     */
    @VisibleForTesting
    void deleteInstancesWithJournal(List<MaterialInstanceDO> instances, String operationId) {
        if (CollUtil.isEmpty(instances)) {
            return;
        }
        Set<Long> idSet = instances.stream().map(MaterialInstanceDO::getId).collect(Collectors.toSet());
        List<MaterialInstanceDO> deleteOrder = instances.stream()
                .sorted(Comparator.comparingInt(
                        (MaterialInstanceDO instance) -> pathDepth(instance.getInstancePath())).reversed())
                .toList();
        for (MaterialInstanceDO instance : deleteOrder) {
            boolean forestRoot = instance.getParentInstanceId() == null
                    || !idSet.contains(instance.getParentInstanceId());
            if (forestRoot) {
                SlotDO fromSlot = instance.getRootSlotId() == null ? null : slotMapper.selectById(instance.getRootSlotId());
                MaterialMovementDO movement = buildMovement(MovementTypeEnum.DELETE.getType(), instance, fromSlot, null);
                movement.setOperationId(operationId);
                movement.setBeforeStatus(instance.getInstanceStatus());
                // afterStatus 留空：实例已随删除离开系统，没有后续状态
                materialMovementService.recordMovement(movement);
            }
            materialInstanceMapper.deleteById(instance.getId());
        }
        // 释放这些实例占用的槽位
        instances.stream()
                .map(MaterialInstanceDO::getRootSlotId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::refreshSlotOccupancy);
    }

    /**
     * 物化路径深度：'/' 分隔的段数。'/1' 是 1 层，'/1/5' 是 2 层；空路径按 0 层。
     */
    private static int pathDepth(String instancePath) {
        if (StrUtil.isBlank(instancePath)) {
            return 0;
        }
        return instancePath.split("/").length - 1;
    }


    // ==================== 上架 / 下架 / 转移 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchPutIn(MaterialInstanceBatchPutInReqVO reqVO) {
        // 1. 槽位列表非空
        if (CollUtil.isEmpty(reqVO.getSlotIds())) {
            throw exception(BATCH_PUT_IN_SLOTS_EMPTY);
        }
        // 2. 解析物料 → 确定容器类型 / 内容物定义信息
        String kind = reqVO.getMaterialKind();
        String materialCode;
        String materialName;
        Long contentDefId = null;
        String contentDefCode = null;
        String contentType = null;
        Long containerTypeId = reqVO.getContainerTypeId();
        String containerTypeCode = reqVO.getContainerTypeCode();
        // 容器类型设置的最大数量（枪头盒/吸头等），非空时批量上架默认当前数量取该值
        Integer maxCount = null;
        // 容器最大容积：试剂（CONTENT）上架时默认当前体积取该值
        BigDecimal defaultVolUl = null;
        // 当前物料的容器类型（CONTENT 为装载容器，CONTAINER 为容器本身），用于 RACK 子集判断
        ContainerTypeDO containerType = null;

        if ("CONTENT".equalsIgnoreCase(kind)) {
            ContentDefDO cd = contentDefMapper.selectById(reqVO.getMaterialId());
            if (cd == null) {
                throw exception(CONTENT_DEF_NOT_EXISTS);
            }
            materialCode = cd.getContentCode();
            materialName = cd.getContentName();
            contentDefId = cd.getId();
            contentDefCode = cd.getContentCode();
            contentType = cd.getContentType();
            // 试剂类物料必须指定装载容器
            if (containerTypeId == null) {
                throw exception(BATCH_PUT_IN_CONTAINER_REQUIRED);
            }
            ContainerTypeDO ct = containerTypeMapper.selectById(containerTypeId);
            if (ct == null) {
                throw exception(CONTAINER_TYPE_NOT_EXISTS);
            }
            containerType = ct;
            containerTypeCode = ct.getTypeCode();
            maxCount = ct.getMaxCount();
            defaultVolUl = ct.getMaxVolUl();
        } else if ("CONTAINER".equalsIgnoreCase(kind)) {
            ContainerTypeDO ct = containerTypeMapper.selectById(reqVO.getMaterialId());
            if (ct == null) {
                throw exception(CONTAINER_TYPE_NOT_EXISTS);
            }
            materialCode = ct.getTypeCode();
            materialName = ct.getTypeName();
            containerTypeId = ct.getId();
            containerTypeCode = ct.getTypeCode();
            containerType = ct;
            maxCount = ct.getMaxCount();
            // 空容器：内容物留空
        } else {
            throw exception(BATCH_PUT_IN_MATERIAL_KIND_INVALID, kind);
        }

        String status = StrUtil.blankToDefault(reqVO.getInstanceStatus(), InstanceStatusEnum.AVAILABLE.name());

        // 3. 托盘架（RACK）自动生成子集：载体 + 子单元类型 + 位数都齐备时才展开，否则只生成 1 个顶层实例
        ContainerTypeDO childType = resolveChildContainerType(containerType);
        int perSlotCount = childType != null && containerType.getWellCount() != null
                ? 1 + containerType.getWellCount() : 1;
        if ((long) reqVO.getSlotIds().size() * perSlotCount > MAX_BATCH_SIZE) {
            throw exception(MATERIAL_INSTANCE_BATCH_SIZE_LIMIT, MAX_BATCH_SIZE);
        }

        // 4. 每个槽位各组装 1 个顶层实例（RACK 时再展开 wellCount 个子实例），共享 operationId 归组流水
        String operationId = IdUtil.fastSimpleUUID();
        List<Long> ids = new ArrayList<>(reqVO.getSlotIds().size() * perSlotCount);
        for (Long slotId : reqVO.getSlotIds()) {
            SlotDO slot = slotMapper.selectById(slotId);
            if (slot == null) {
                throw exception(SLOT_NOT_EXISTS);
            }
            // 顶层实例（一槽一实例）
            MaterialInstanceSaveReqVO top = new MaterialInstanceSaveReqVO();
            top.setInstanceCode(buildInstanceCode(materialCode));
            top.setInstanceName(materialName);
            top.setContainerTypeId(containerTypeId);
            top.setContainerTypeCode(containerTypeCode);
            top.setContentDefId(contentDefId);
            top.setContentDefCode(contentDefCode);
            top.setContentType(contentType);
            top.setRootSlotId(slotId);
            top.setInstanceStatus(status);
            // 试剂上架：当前体积默认取容器最大容积；否则用请求传入值
            top.setCurrentVolUl(defaultVolUl != null ? defaultVolUl : reqVO.getCurrentVolUl());
            // 容器类型设置了最大数量时，实例当前数量默认取最大值（否则用请求传入值，再否则为空=单件）
            top.setCurrentCount(maxCount != null ? maxCount : reqVO.getCurrentCount());
            top.setBarcode(reqVO.getBarcode());
            top.setDescription(reqVO.getDescription());
            Long topId = doCreateMaterialInstance(top, operationId, null);
            ids.add(topId);

            // 子实例：挂在顶层之下，占位到 A1..C2 等孔位，不占槽位、不单独记流水
            if (childType != null) {
                for (String pos : buildPositionCodes(containerType)) {
                    MaterialInstanceSaveReqVO child = new MaterialInstanceSaveReqVO();
                    child.setInstanceCode(buildInstanceCode(childType.getTypeCode()));
                    child.setInstanceName(childType.getTypeName());
                    child.setContainerTypeId(childType.getId());
                    child.setContainerTypeCode(childType.getTypeCode());
                    child.setContentType("EMPTY"); // 空容器
                    child.setParentInstanceId(topId);
                    child.setParentPositionCode(pos);
                    child.setInstanceStatus(status);
                    child.setDescription(reqVO.getDescription());
                    ids.add(doCreateMaterialInstance(child, operationId, null));
                }
            }
        }
        return ids;
    }

    /**
     * 反查托盘的子单元容器类型：仅当载体的物理形态是 RACK（托盘架）且配置了 childTypeCode 时才返回，
     * 否则返回 null（表示不自动生成子集）。
     */
    private ContainerTypeDO resolveChildContainerType(ContainerTypeDO carrier) {
        if (carrier == null || !"RACK".equalsIgnoreCase(carrier.getCategory())) {
            return null;
        }
        if (StrUtil.isBlank(carrier.getChildTypeCode())) {
            return null;
        }
        return containerTypeMapper.selectOne(ContainerTypeDO::getTypeCode, carrier.getChildTypeCode());
    }

    /**
     * 生成托盘的子孔位位置码列表。
     * ROW_COL 按「行字母 + 列数字」展开（wellRows 行 × wellCols 列，行优先），
     * SEQ / 其他按 1..wellCount 顺序号。
     */
    private List<String> buildPositionCodes(ContainerTypeDO carrier) {
        List<String> codes = new ArrayList<>();
        int total = carrier.getWellCount() == null ? 0 : carrier.getWellCount();
        if (total <= 0) {
            return codes;
        }
        if ("SEQ".equalsIgnoreCase(carrier.getPositionNaming())) {
            for (int i = 1; i <= total; i++) {
                codes.add(String.valueOf(i));
            }
            return codes;
        }
        // 默认 ROW_COL：行字母（A、B、…）+ 列数字（1、2、…）
        int rows = carrier.getWellRows() == null ? 1 : carrier.getWellRows();
        int cols = carrier.getWellCols() == null ? 1 : carrier.getWellCols();
        if (rows * cols < total) {
            // 行列乘积不足以表达总位数时退化为顺序号，避免丢位
            for (int i = 1; i <= total; i++) {
                codes.add(String.valueOf(i));
            }
            return codes;
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 1; c <= cols; c++) {
                codes.add(toExcelColumn(r) + c);
            }
        }
        return codes;
    }

    /**
     * 0 基索引 → Excel 列字母（0→A、1→B、…、25→Z、26→AA）
     */
    private String toExcelColumn(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            n--;
            sb.append((char) ('A' + n % 26));
            n /= 26;
        }
        return sb.reverse().toString();
    }

    /**
     * 生成实例编码：物料编码 + 雪花后缀，保证唯一且不超过 64 字符
     */
    private String buildInstanceCode(String materialCode) {
        String prefix = StrUtil.isBlank(materialCode) ? "INST" : materialCode;
        if (prefix.length() > 40) {
            prefix = prefix.substring(0, 40);
        }
        return prefix + "_" + IdUtil.getSnowflakeNextIdStr();
    }

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
        MaterialMovementDO movement = buildMovement(MovementTypeEnum.PUT_IN.getType(), instance, null, slot);
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
    public int batchTakeOut(List<Long> slotIds) {
        if (CollUtil.isEmpty(slotIds)) {
            return 0;
        }
        // 一次批量下架的 DELETE 流水共享一个 operationId，便于在流水页还原「一次操作」
        String operationId = IdUtil.fastSimpleUUID();
        List<MaterialInstanceDO> toDelete = new ArrayList<>(slotIds.size());
        for (Long slotId : slotIds) {
            // 找该槽位上的顶层实例（一槽一实例，selectList 取首条以防脏数据多条）
            List<MaterialInstanceDO> insts = materialInstanceMapper.selectList(
                    new LambdaQueryWrapperX<MaterialInstanceDO>()
                            .eq(MaterialInstanceDO::getRootSlotId, slotId)
                            .isNull(MaterialInstanceDO::getParentInstanceId));
            if (CollUtil.isEmpty(insts)) {
                continue; // 空槽位跳过
            }
            MaterialInstanceDO instance = insts.get(0);
            // 校验无子实例：批量下架是删除语义，带着子实例的容器不允许在此删除（与单条删除口径一致）
            Long childCount = materialInstanceMapper.selectCount(MaterialInstanceDO::getParentInstanceId, instance.getId());
            if (childCount != null && childCount > 0) {
                throw exception(MATERIAL_INSTANCE_HAS_CHILDREN, instance.getId());
            }
            toDelete.add(instance);
        }
        // 复用删除逻辑：记 DELETE 流水 + 删除 + 释放槽位占用
        deleteInstancesWithJournal(toDelete, operationId);
        return toDelete.size();
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

        MaterialMovementDO movement = buildMovement(MovementTypeEnum.TAKE_OUT.getType(), instance, fromSlot, null);
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
        MaterialMovementDO movement = buildMovement(MovementTypeEnum.MOVE.getType(), instance, fromSlot, toSlot);
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
    public MaterialInstanceConsumeRespVO consume(MaterialInstanceConsumeReqVO reqVO) {
        // D1-a：同业务幂等键已有流水 → 直接回放，不再改库存
        MaterialInstanceConsumeRespVO replay = findConsumeReplay(reqVO.getIdempotentKey());
        if (replay != null) {
            return replay;
        }
        try {
            return doConsumeBatch(reqVO);
        } catch (ServiceException ex) {
            return replayOrRethrow(reqVO.getIdempotentKey(), ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialInstanceConsumeRespVO consumeByContainer(MaterialInstanceConsumeByContainerReqVO reqVO) {
        MaterialInstanceConsumeRespVO replay = findConsumeReplay(reqVO.getIdempotentKey());
        if (replay != null) {
            return replay;
        }
        try {
            return doConsumeByContainerBatch(reqVO);
        } catch (ServiceException ex) {
            return replayOrRethrow(reqVO.getIdempotentKey(), ex);
        }
    }

    /**
     * 精确消耗实际执行（无预查）。同批明细共享 operationId。
     */
    private MaterialInstanceConsumeRespVO doConsumeBatch(MaterialInstanceConsumeReqVO reqVO) {
        String operationId = StrUtil.blankToDefault(reqVO.getOperationId(), IdUtil.fastSimpleUUID());
        List<Long> movementIds = new ArrayList<>(reqVO.getItems().size());
        List<Long> consumedInstanceIds = new ArrayList<>(reqVO.getItems().size());
        int index = 0;
        for (MaterialInstanceConsumeReqVO.Item item : reqVO.getItems()) {
            MaterialInstanceDO instance = validateMaterialInstanceExists(item.getInstanceId());
            Long movementId = doConsume(instance, item.getQty(), item.getVolUl(),
                    item.getAfterStatus(), StrUtil.blankToDefault(item.getRemark(), reqVO.getRemark()),
                    reqVO, operationId, index++);
            movementIds.add(movementId);
            consumedInstanceIds.add(instance.getId());
        }
        return MaterialInstanceConsumeRespVO.builder()
                .movementIds(movementIds)
                .consumedInstanceIds(consumedInstanceIds)
                .operationId(operationId)
                .idempotentReplay(Boolean.FALSE)
                .build();
    }

    /**
     * 按容器消耗实际执行（无预查）。
     */
    private MaterialInstanceConsumeRespVO doConsumeByContainerBatch(MaterialInstanceConsumeByContainerReqVO reqVO) {
        // 先锁容器行，两笔按容器消耗会在这里排开；锁到之后再读子实例，避免用加锁前的快照挑选
        MaterialInstanceDO container = materialInstanceMapper.selectOneForUpdate(
                MaterialInstanceDO::getId, reqVO.getRootInstanceId());
        if (container == null) {
            throw exception(MATERIAL_INSTANCE_NOT_EXISTS);
        }
        // 子实例按主键顺序加锁。位置序仍在内存里做（A10 不能排在 A2 前面），但用的是加锁后的行
        List<MaterialInstanceDO> children = materialInstanceMapper.selectChildrenForUpdate(reqVO.getRootInstanceId());
        if (CollUtil.isEmpty(children)) {
            throw exception(INSTANCE_CONSUME_NO_CHILD, container.getInstanceCode());
        }
        // 只挑可消耗状态的；默认不含 RESERVED。includeReserved 仅含无归属旧 RESERVED，绝不抢有归属预留。
        boolean includeReserved = Boolean.TRUE.equals(reqVO.getIncludeReserved());
        List<MaterialInstanceDO> candidates = children.stream()
                .filter(c -> {
                    if (StrUtil.isNotBlank(c.getReservationId())) {
                        return false; // 有归属必须走 consumeReservation
                    }
                    if (InstanceStatusEnum.AVAILABLE.name().equals(c.getInstanceStatus())) {
                        return true;
                    }
                    return includeReserved && InstanceStatusEnum.RESERVED.name().equals(c.getInstanceStatus());
                })
                .sorted(positionComparator(reqVO.getStrategy()))
                .toList();
        if (candidates.size() < reqVO.getCount()) {
            throw exception(INSTANCE_CONSUME_NOT_ENOUGH_CHILD, container.getInstanceCode(),
                    reqVO.getCount(), candidates.size());
        }

        String operationId = StrUtil.blankToDefault(reqVO.getOperationId(), IdUtil.fastSimpleUUID());
        String afterStatus = StrUtil.blankToDefault(reqVO.getAfterStatus(), InstanceStatusEnum.USED.name());
        List<Long> movementIds = new ArrayList<>(reqVO.getCount());
        List<Long> consumedIds = new ArrayList<>(reqVO.getCount());
        int index = 0;
        for (MaterialInstanceDO child : candidates.subList(0, reqVO.getCount())) {
            // 每个被挑中的子实例整件消耗掉（一件实物 = 一条流水）
            Long movementId = doConsume(child, null, null, afterStatus, reqVO.getRemark(), reqVO, operationId, index++);
            movementIds.add(movementId);
            consumedIds.add(child.getId());
        }
        return MaterialInstanceConsumeRespVO.builder()
                .movementIds(movementIds)
                .consumedInstanceIds(consumedIds)
                .operationId(operationId)
                .idempotentReplay(Boolean.FALSE)
                .build();
    }

    /**
     * 并发/重试撞键：回滚本事务未提交变更，回放首次消耗结果。
     */
    private MaterialInstanceConsumeRespVO replayOrRethrow(String businessIdempotentKey, ServiceException ex) {
        if (StrUtil.isBlank(businessIdempotentKey) || !isConsumeIdempotentReplayCandidate(ex)) {
            throw ex;
        }
        MaterialInstanceConsumeRespVO replay = findConsumeReplay(businessIdempotentKey);
        if (replay == null) {
            throw ex;
        }
        // 单测无 Spring 事务代理时跳过；正式路径由 @Transactional 包住
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return replay;
    }

    /**
     * 可尝试按幂等键回放的错误：流水撞键、库存 CAS 冲突、状态已不可消耗（常见于整件消耗后的重试）。
     */
    private static boolean isConsumeIdempotentReplayCandidate(ServiceException ex) {
        Integer code = ex.getCode();
        return Objects.equals(code, MOVEMENT_IDEMPOTENT_DUPLICATE.getCode())
                || Objects.equals(code, INSTANCE_CONSUME_CONFLICT.getCode())
                || Objects.equals(code, INSTANCE_CONSUME_STATUS_ILLEGAL.getCode());
    }

    /**
     * 按调用方业务幂等键加载首次消耗结果；无流水返回 null。
     */
    private MaterialInstanceConsumeRespVO findConsumeReplay(String businessIdempotentKey) {
        if (StrUtil.isBlank(businessIdempotentKey)) {
            return null;
        }
        List<MaterialMovementDO> movements =
                materialMovementService.listByBusinessIdempotentKey(businessIdempotentKey);
        if (CollUtil.isEmpty(movements)) {
            return null;
        }
        List<Long> movementIds = new ArrayList<>(movements.size());
        List<Long> consumedInstanceIds = new ArrayList<>(movements.size());
        for (MaterialMovementDO movement : movements) {
            movementIds.add(movement.getId());
            consumedInstanceIds.add(movement.getInstanceId());
        }
        return MaterialInstanceConsumeRespVO.builder()
                .movementIds(movementIds)
                .consumedInstanceIds(consumedInstanceIds)
                .operationId(movements.get(0).getOperationId())
                .idempotentReplay(Boolean.TRUE)
                .build();
    }


    // ==================== 检查 / 最小 use / release（Phase D） ====================

    @Override
    public MaterialInstanceCheckRespVO checkAvailability(MaterialInstanceCheckReqVO reqVO) {
        List<MaterialInstanceCheckRespVO.Item> results = new ArrayList<>();
        boolean ok = true;
        for (MaterialInstanceCheckReqVO.Item item : reqVO.getItems()) {
            MaterialInstanceCheckRespVO.Item result = new MaterialInstanceCheckRespVO.Item();
            result.setInstanceId(item.getInstanceId());
            if (item.getInstanceId() == null) {
                result.setAvailable(Boolean.FALSE);
                result.setReason("实例编号为空");
                ok = false;
                results.add(result);
                continue;
            }
            MaterialInstanceDO instance = materialInstanceMapper.selectById(item.getInstanceId());
            if (instance == null) {
                result.setAvailable(Boolean.FALSE);
                result.setReason("实例不存在");
                ok = false;
                results.add(result);
                continue;
            }
            result.setInstanceCode(instance.getInstanceCode());
            result.setInstanceStatus(instance.getInstanceStatus());
            String reason = null;
            if (!InstanceStatusEnum.isConsumable(instance.getInstanceStatus())) {
                reason = "状态不可用：" + instance.getInstanceStatus();
            } else if (item.getMinQty() != null) {
                int current = instance.getCurrentCount() == null ? 1 : instance.getCurrentCount();
                if (current < item.getMinQty()) {
                    reason = "数量不足：需要 " + item.getMinQty() + "，剩余 " + current;
                }
            }
            if (reason == null && item.getMinVolUl() != null) {
                BigDecimal currentVol = instance.getCurrentVolUl() == null ? BigDecimal.ZERO : instance.getCurrentVolUl();
                if (currentVol.compareTo(item.getMinVolUl()) < 0) {
                    reason = "体积不足：需要 " + item.getMinVolUl() + "μL，剩余 " + currentVol + "μL";
                }
            }
            if (reason != null) {
                result.setAvailable(Boolean.FALSE);
                result.setReason(reason);
                ok = false;
            } else {
                result.setAvailable(Boolean.TRUE);
            }
            results.add(result);
        }
        return MaterialInstanceCheckRespVO.builder().ok(ok).items(results).build();
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialInstanceUseReleaseRespVO use(MaterialInstanceUseReqVO reqVO) {
        MaterialInstanceUseReleaseRespVO replay = findUseReleaseReplay(reqVO.getIdempotentKey(),
                MovementTypeEnum.STATUS_CHANGE.getType());
        if (replay != null) {
            return replay;
        }
        try {
            return doUse(reqVO);
        } catch (ServiceException ex) {
            return replayUseReleaseOrRethrow(reqVO.getIdempotentKey(), MovementTypeEnum.STATUS_CHANGE.getType(), ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialInstanceUseReleaseRespVO release(MaterialInstanceReleaseReqVO reqVO) {
        MaterialInstanceUseReleaseRespVO replay = findUseReleaseReplay(reqVO.getIdempotentKey(),
                MovementTypeEnum.RELEASE.getType());
        if (replay != null) {
            return replay;
        }
        try {
            return doRelease(reqVO);
        } catch (ServiceException ex) {
            return replayUseReleaseOrRethrow(reqVO.getIdempotentKey(), MovementTypeEnum.RELEASE.getType(), ex);
        }
    }

    private MaterialInstanceUseReleaseRespVO doUse(MaterialInstanceUseReqVO reqVO) {
        MaterialInstanceDO instance = validateMaterialInstanceExists(reqVO.getInstanceId());
        String beforeStatus = instance.getInstanceStatus();
        boolean owned = StrUtil.isNotBlank(instance.getReservationId());
        if (owned) {
            assertOwnership(instance, reqVO.getReservationId(), reqVO.getExecutionId());
            assertReservationNotExpired(instance);
            if (!InstanceStatusEnum.RESERVED.name().equals(beforeStatus)) {
                throw exception(INSTANCE_USE_STATUS_ILLEGAL, instance.getInstanceCode(), beforeStatus);
            }
        } else {
            if (!InstanceStatusEnum.AVAILABLE.name().equals(beforeStatus)
                    && !InstanceStatusEnum.RESERVED.name().equals(beforeStatus)) {
                throw exception(INSTANCE_USE_STATUS_ILLEGAL, instance.getInstanceCode(), beforeStatus);
            }
        }
        String toStatus = InstanceStatusEnum.IN_USE.name();
        String operationId = StrUtil.blankToDefault(reqVO.getOperationId(), IdUtil.fastSimpleUUID());
        MaterialMovementDO movement = buildMovement(MovementTypeEnum.STATUS_CHANGE.getType(), instance, null, null);
        applyTrace(movement, reqVO, operationId, 0);
        movement.setBeforeStatus(beforeStatus);
        movement.setAfterStatus(toStatus);

        LambdaUpdateWrapper<MaterialInstanceDO> update = new LambdaUpdateWrapper<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getId, instance.getId())
                .eq(MaterialInstanceDO::getInstanceStatus, beforeStatus)
                .set(MaterialInstanceDO::getInstanceStatus, toStatus);
        if (!owned && InstanceStatusEnum.RESERVED.name().equals(beforeStatus)) {
            update.set(MaterialInstanceDO::getReservationId, null)
                    .set(MaterialInstanceDO::getReservedByExecutionId, null)
                    .set(MaterialInstanceDO::getReservedUntil, null);
        }
        if (materialInstanceMapper.update(null, update) != 1) {
            throw exception(INSTANCE_CONSUME_CONFLICT, instance.getInstanceCode());
        }
        Long movementId = materialMovementService.recordMovement(movement);
        return MaterialInstanceUseReleaseRespVO.builder()
                .movementId(movementId)
                .instanceId(instance.getId())
                .beforeStatus(beforeStatus)
                .afterStatus(toStatus)
                .operationId(operationId)
                .idempotentReplay(Boolean.FALSE)
                .build();
    }

    private MaterialInstanceUseReleaseRespVO doRelease(MaterialInstanceReleaseReqVO reqVO) {
        MaterialInstanceDO instance = validateMaterialInstanceExists(reqVO.getInstanceId());
        String beforeStatus = instance.getInstanceStatus();
        boolean owned = StrUtil.isNotBlank(instance.getReservationId());
        if (owned) {
            assertOwnership(instance, reqVO.getReservationId(), reqVO.getExecutionId());
            if (!InstanceStatusEnum.RESERVED.name().equals(beforeStatus)) {
                throw exception(INSTANCE_RELEASE_STATUS_ILLEGAL, instance.getInstanceCode(), beforeStatus);
            }
        } else {
            if (!InstanceStatusEnum.IN_USE.name().equals(beforeStatus)
                    && !InstanceStatusEnum.RESERVED.name().equals(beforeStatus)) {
                throw exception(INSTANCE_RELEASE_STATUS_ILLEGAL, instance.getInstanceCode(), beforeStatus);
            }
        }
        String toStatus = InstanceStatusEnum.AVAILABLE.name();
        String operationId = StrUtil.blankToDefault(reqVO.getOperationId(), IdUtil.fastSimpleUUID());
        MaterialMovementDO movement = buildMovement(MovementTypeEnum.RELEASE.getType(), instance, null, null);
        applyTrace(movement, reqVO, operationId, 0);
        movement.setBeforeStatus(beforeStatus);
        movement.setAfterStatus(toStatus);

        LambdaUpdateWrapper<MaterialInstanceDO> update = new LambdaUpdateWrapper<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getId, instance.getId())
                .eq(MaterialInstanceDO::getInstanceStatus, beforeStatus)
                .set(MaterialInstanceDO::getInstanceStatus, toStatus)
                .set(MaterialInstanceDO::getReservationId, null)
                .set(MaterialInstanceDO::getReservedByExecutionId, null)
                .set(MaterialInstanceDO::getReservedUntil, null);
        if (owned) {
            update.eq(MaterialInstanceDO::getReservationId, instance.getReservationId())
                    .eq(MaterialInstanceDO::getReservedByExecutionId, instance.getReservedByExecutionId());
        }
        if (materialInstanceMapper.update(null, update) != 1) {
            throw exception(INSTANCE_CONSUME_CONFLICT, instance.getInstanceCode());
        }
        Long movementId = materialMovementService.recordMovement(movement);
        return MaterialInstanceUseReleaseRespVO.builder()
                .movementId(movementId)
                .instanceId(instance.getId())
                .beforeStatus(beforeStatus)
                .afterStatus(toStatus)
                .operationId(operationId)
                .idempotentReplay(Boolean.FALSE)
                .build();
    }

    private MaterialInstanceUseReleaseRespVO replayUseReleaseOrRethrow(String businessIdempotentKey,
                                                                       String expectedType,
                                                                       ServiceException ex) {
        if (StrUtil.isBlank(businessIdempotentKey) || !isConsumeIdempotentReplayCandidate(ex)) {
            throw ex;
        }
        MaterialInstanceUseReleaseRespVO replay = findUseReleaseReplay(businessIdempotentKey, expectedType);
        if (replay == null) {
            throw ex;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return replay;
    }

    private MaterialInstanceUseReleaseRespVO findUseReleaseReplay(String businessIdempotentKey, String expectedType) {
        if (StrUtil.isBlank(businessIdempotentKey)) {
            return null;
        }
        List<MaterialMovementDO> movements =
                materialMovementService.listByBusinessIdempotentKey(businessIdempotentKey);
        if (CollUtil.isEmpty(movements)) {
            return null;
        }
        MaterialMovementDO first = movements.get(0);
        if (StrUtil.isNotBlank(expectedType) && !expectedType.equals(first.getMovementType())) {
            throw exception(MOVEMENT_IDEMPOTENT_CONFLICT, businessIdempotentKey);
        }
        return MaterialInstanceUseReleaseRespVO.builder()
                .movementId(first.getId())
                .instanceId(first.getInstanceId())
                .beforeStatus(first.getBeforeStatus())
                .afterStatus(first.getAfterStatus())
                .operationId(first.getOperationId())
                .idempotentReplay(Boolean.TRUE)
                .build();
    }

    // ==================== W-2.1 实物化 / W-2.2 预留释放 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialInstanceInstantiateRespVO instantiateSample(MaterialInstanceInstantiateReqVO reqVO) {
        if (reqVO == null || StrUtil.isBlank(reqVO.getSampleId()) || StrUtil.isBlank(reqVO.getContainerTypeCode())
                || reqVO.getQuantity() == null || reqVO.getQuantity() <= 0) {
            throw exception(SAMPLE_INSTANTIATE_PARAM_INVALID, "sampleId/containerTypeCode/quantity 必填且 quantity>0");
        }
        if (reqVO.getQuantity() > MAX_BATCH_SIZE) {
            throw exception(MATERIAL_INSTANCE_BATCH_SIZE_LIMIT, MAX_BATCH_SIZE);
        }
        if (StrUtil.isNotBlank(reqVO.getIdempotentKey())) {
            MaterialInstanceInstantiateRespVO replay = findInstantiateReplay(reqVO.getIdempotentKey(), reqVO.getSampleId());
            if (replay != null) {
                return replay;
            }
        }
        SampleInfoDO sample = sampleInfoMapper.selectBySampleId(reqVO.getSampleId());
        if (sample == null) {
            throw exception(SAMPLE_NOT_EXISTS);
        }
        ContainerTypeDO containerType = containerTypeMapper.selectByTypeCode(reqVO.getContainerTypeCode());
        if (containerType == null) {
            throw exception(CONTAINER_TYPE_NOT_EXISTS);
        }
        BigDecimal vol = reqVO.getInitialVolUl() != null ? reqVO.getInitialVolUl() : sample.getInitialVolUl();

        String operationId = IdUtil.fastSimpleUUID();
        List<Long> ids = new ArrayList<>(reqVO.getQuantity());
        MovementTraceBaseReqVO trace = new MovementTraceBaseReqVO();
        trace.setIdempotentKey(reqVO.getIdempotentKey());
        trace.setBizSource("FLOW");
        trace.setOperatorType("AUTO");
        trace.setOperationId(operationId);
        try {
            for (int i = 0; i < reqVO.getQuantity(); i++) {
                MaterialInstanceSaveReqVO save = new MaterialInstanceSaveReqVO();
                save.setInstanceCode("SPINST-" + IdUtil.getSnowflakeNextIdStr());
                save.setInstanceName(sample.getSampleName());
                save.setContainerTypeId(containerType.getId());
                save.setContainerTypeCode(containerType.getTypeCode());
                save.setSampleId(reqVO.getSampleId());
                save.setContentDefCode(sample.getContentDefCode());
                save.setContentDefId(sample.getContentDefId());
                save.setCurrentVolUl(vol);
                save.setCurrentCount(1);
                save.setInstanceStatus(InstanceStatusEnum.AVAILABLE.name());
                ids.add(doCreateMaterialInstance(save, operationId, null, trace, i));
            }
        } catch (ServiceException ex) {
            if (StrUtil.isNotBlank(reqVO.getIdempotentKey()) && isConsumeIdempotentReplayCandidate(ex)) {
                MaterialInstanceInstantiateRespVO replay = findInstantiateReplay(reqVO.getIdempotentKey(), reqVO.getSampleId());
                if (replay != null) {
                    if (TransactionSynchronizationManager.isActualTransactionActive()) {
                        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    }
                    return replay;
                }
            }
            throw ex;
        }
        return MaterialInstanceInstantiateRespVO.builder()
                .sampleId(reqVO.getSampleId())
                .instanceIds(ids)
                .operationId(operationId)
                .idempotentReplay(Boolean.FALSE)
                .build();
    }

    private MaterialInstanceInstantiateRespVO findInstantiateReplay(String businessIdempotentKey, String sampleId) {
        List<MaterialMovementDO> movements = materialMovementService.listByBusinessIdempotentKey(businessIdempotentKey);
        if (CollUtil.isEmpty(movements)) {
            return null;
        }
        for (MaterialMovementDO m : movements) {
            if (!MovementTypeEnum.CREATE.getType().equals(m.getMovementType())) {
                throw exception(MOVEMENT_IDEMPOTENT_CONFLICT, businessIdempotentKey);
            }
        }
        List<Long> ids = new ArrayList<>(movements.size());
        for (MaterialMovementDO m : movements) {
            ids.add(m.getInstanceId());
        }
        return MaterialInstanceInstantiateRespVO.builder()
                .sampleId(sampleId)
                .instanceIds(ids)
                .operationId(movements.get(0).getOperationId())
                .idempotentReplay(Boolean.TRUE)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialInstanceReserveRespVO reserve(MaterialInstanceReserveReqVO reqVO) {
        if (reqVO == null || CollUtil.isEmpty(reqVO.getInstanceIds())) {
            throw exception(INSTANCE_RESERVE_EMPTY);
        }
        if (StrUtil.isBlank(reqVO.getExecutionId())) {
            throw exception(SAMPLE_INSTANTIATE_PARAM_INVALID, "executionId 不能为空");
        }
        if (reqVO.getReservedUntil() == null || !reqVO.getReservedUntil().isAfter(LocalDateTime.now())) {
            throw exception(INSTANCE_RESERVE_UNTIL_INVALID);
        }
        List<Long> instanceIds = reqVO.getInstanceIds().stream().filter(Objects::nonNull).distinct().toList();
        if (instanceIds.size() != reqVO.getInstanceIds().size()) {
            throw exception(INSTANCE_RESERVE_DUPLICATE);
        }
        MaterialInstanceReserveRespVO replay = findReserveReplay(reqVO.getIdempotentKey());
        if (replay != null) {
            return replay;
        }
        try {
            return doReserve(reqVO, instanceIds);
        } catch (ServiceException ex) {
            if (StrUtil.isBlank(reqVO.getIdempotentKey()) || !isConsumeIdempotentReplayCandidate(ex)) {
                throw ex;
            }
            MaterialInstanceReserveRespVO r = findReserveReplay(reqVO.getIdempotentKey());
            if (r == null) {
                throw ex;
            }
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            return r;
        }
    }

    private MaterialInstanceReserveRespVO doReserve(MaterialInstanceReserveReqVO reqVO, List<Long> instanceIds) {
        String reservationId = IdUtil.fastSimpleUUID();
        String operationId = StrUtil.blankToDefault(reqVO.getOperationId(), IdUtil.fastSimpleUUID());
        List<Long> movementIds = new ArrayList<>(instanceIds.size());
        int index = 0;
        for (Long instanceId : instanceIds) {
            MaterialInstanceDO instance = validateMaterialInstanceExists(instanceId);
            if (!InstanceStatusEnum.AVAILABLE.name().equals(instance.getInstanceStatus())) {
                throw exception(INSTANCE_RESERVE_STATUS_ILLEGAL, instance.getInstanceCode(), instance.getInstanceStatus());
            }
            if (StrUtil.isNotBlank(instance.getReservationId())) {
                throw exception(INSTANCE_ALREADY_RESERVED, instance.getInstanceCode(), instance.getReservationId());
            }
            MaterialMovementDO movement = buildMovement(MovementTypeEnum.RESERVE.getType(), instance, null, null);
            applyTrace(movement, reqVO, operationId, index++);
            movement.setBeforeStatus(InstanceStatusEnum.AVAILABLE.name());
            movement.setAfterStatus(InstanceStatusEnum.RESERVED.name());
            movement.setRefType(StrUtil.blankToDefault(reqVO.getRefType(), "EXECUTION"));
            movement.setRefId(StrUtil.blankToDefault(reqVO.getRefId(), reqVO.getExecutionId()));

            LambdaUpdateWrapper<MaterialInstanceDO> update = new LambdaUpdateWrapper<MaterialInstanceDO>()
                    .eq(MaterialInstanceDO::getId, instance.getId())
                    .eq(MaterialInstanceDO::getInstanceStatus, InstanceStatusEnum.AVAILABLE.name())
                    .isNull(MaterialInstanceDO::getReservationId)
                    .set(MaterialInstanceDO::getInstanceStatus, InstanceStatusEnum.RESERVED.name())
                    .set(MaterialInstanceDO::getReservationId, reservationId)
                    .set(MaterialInstanceDO::getReservedByExecutionId, reqVO.getExecutionId())
                    .set(MaterialInstanceDO::getReservedUntil, reqVO.getReservedUntil());
            if (materialInstanceMapper.update(null, update) != 1) {
                throw exception(INSTANCE_CONSUME_CONFLICT, instance.getInstanceCode());
            }
            movementIds.add(materialMovementService.recordMovement(movement));
        }
        return MaterialInstanceReserveRespVO.builder()
                .reservationId(reservationId)
                .instanceIds(instanceIds)
                .movementIds(movementIds)
                .operationId(operationId)
                .idempotentReplay(Boolean.FALSE)
                .build();
    }

    private MaterialInstanceReserveRespVO findReserveReplay(String businessIdempotentKey) {
        if (StrUtil.isBlank(businessIdempotentKey)) {
            return null;
        }
        List<MaterialMovementDO> movements = materialMovementService.listByBusinessIdempotentKey(businessIdempotentKey);
        if (CollUtil.isEmpty(movements)) {
            return null;
        }
        for (MaterialMovementDO m : movements) {
            if (!MovementTypeEnum.RESERVE.getType().equals(m.getMovementType())) {
                throw exception(MOVEMENT_IDEMPOTENT_CONFLICT, businessIdempotentKey);
            }
        }
        // 从实例读取同批 reservationId
        MaterialInstanceDO firstInst = materialInstanceMapper.selectById(movements.get(0).getInstanceId());
        String reservationId = firstInst == null ? null : firstInst.getReservationId();
        List<Long> instanceIds = new ArrayList<>(movements.size());
        List<Long> movementIds = new ArrayList<>(movements.size());
        for (MaterialMovementDO m : movements) {
            movementIds.add(m.getId());
            instanceIds.add(m.getInstanceId());
        }
        return MaterialInstanceReserveRespVO.builder()
                .reservationId(reservationId)
                .instanceIds(instanceIds)
                .movementIds(movementIds)
                .operationId(movements.get(0).getOperationId())
                .idempotentReplay(Boolean.TRUE)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialInstanceConsumeRespVO consumeReservation(MaterialInstanceConsumeReservationReqVO reqVO) {
        MaterialInstanceConsumeRespVO replay = findConsumeReplay(reqVO.getIdempotentKey());
        if (replay != null) {
            assertReplayType(reqVO.getIdempotentKey(), MovementTypeEnum.CONSUME.getType());
            return replay;
        }
        try {
            return doConsumeReservation(reqVO);
        } catch (ServiceException ex) {
            return replayOrRethrow(reqVO.getIdempotentKey(), ex);
        }
    }

    private MaterialInstanceConsumeRespVO doConsumeReservation(MaterialInstanceConsumeReservationReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getReservationId()) || StrUtil.isBlank(reqVO.getExecutionId())
                || CollUtil.isEmpty(reqVO.getItems())) {
            throw exception(SAMPLE_INSTANTIATE_PARAM_INVALID, "reservationId/executionId/items 不能为空");
        }
        String operationId = StrUtil.blankToDefault(reqVO.getOperationId(), IdUtil.fastSimpleUUID());
        List<Long> movementIds = new ArrayList<>(reqVO.getItems().size());
        List<Long> consumedInstanceIds = new ArrayList<>(reqVO.getItems().size());
        int index = 0;
        for (MaterialInstanceConsumeReqVO.Item item : reqVO.getItems()) {
            MaterialInstanceDO instance = validateMaterialInstanceExists(item.getInstanceId());
            assertOwnership(instance, reqVO.getReservationId(), reqVO.getExecutionId());
            if (InstanceStatusEnum.RESERVED.name().equals(instance.getInstanceStatus())) {
                assertReservationNotExpired(instance);
            } else if (!InstanceStatusEnum.IN_USE.name().equals(instance.getInstanceStatus())) {
                throw exception(INSTANCE_CONSUME_STATUS_ILLEGAL, instance.getInstanceCode(), instance.getInstanceStatus());
            }
            Long movementId = doConsumeOwned(instance, item.getQty(), item.getVolUl(), item.getAfterStatus(),
                    StrUtil.blankToDefault(item.getRemark(), reqVO.getRemark()), reqVO, operationId, index++, true);
            movementIds.add(movementId);
            consumedInstanceIds.add(instance.getId());
        }
        return MaterialInstanceConsumeRespVO.builder()
                .movementIds(movementIds)
                .consumedInstanceIds(consumedInstanceIds)
                .operationId(operationId)
                .idempotentReplay(Boolean.FALSE)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialInstanceReleaseByExecutionRespVO releaseByExecution(MaterialInstanceReleaseByExecutionReqVO reqVO) {
        if (reqVO == null || StrUtil.isBlank(reqVO.getExecutionId())) {
            throw exception(SAMPLE_INSTANTIATE_PARAM_INVALID, "executionId 不能为空");
        }
        MaterialInstanceReleaseByExecutionRespVO replay = findReleaseByExecutionReplay(reqVO.getIdempotentKey());
        if (replay != null) {
            return replay;
        }
        try {
            return doReleaseByExecution(reqVO);
        } catch (ServiceException ex) {
            if (StrUtil.isBlank(reqVO.getIdempotentKey()) || !isConsumeIdempotentReplayCandidate(ex)) {
                throw ex;
            }
            MaterialInstanceReleaseByExecutionRespVO r = findReleaseByExecutionReplay(reqVO.getIdempotentKey());
            if (r == null) {
                throw ex;
            }
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
            return r;
        }
    }

    private MaterialInstanceReleaseByExecutionRespVO doReleaseByExecution(MaterialInstanceReleaseByExecutionReqVO reqVO) {
        List<MaterialInstanceDO> owned = materialInstanceMapper.selectOwnedByExecutionId(reqVO.getExecutionId(), 500);
        String operationId = StrUtil.blankToDefault(reqVO.getOperationId(), IdUtil.fastSimpleUUID());
        List<Long> released = new ArrayList<>();
        List<Long> movementIds = new ArrayList<>();
        List<MaterialInstanceReleaseByExecutionRespVO.Pending> pending = new ArrayList<>();
        int index = 0;
        MovementTraceBaseReqVO trace = reqVO;
        if (StrUtil.isNotBlank(reqVO.getReason())) {
            reqVO.setRemark(reqVO.getReason());
        }
        for (MaterialInstanceDO instance : owned) {
            if (InstanceStatusEnum.IN_USE.name().equals(instance.getInstanceStatus())) {
                pending.add(MaterialInstanceReleaseByExecutionRespVO.Pending.builder()
                        .instanceId(instance.getId())
                        .instanceCode(instance.getInstanceCode())
                        .instanceStatus(instance.getInstanceStatus())
                        .reason("IN_USE 需人工处置，不自动释放")
                        .build());
                continue;
            }
            if (!InstanceStatusEnum.RESERVED.name().equals(instance.getInstanceStatus())) {
                continue;
            }
            MaterialMovementDO movement = buildMovement(MovementTypeEnum.RELEASE.getType(), instance, null, null);
            applyTrace(movement, trace, operationId, index++);
            movement.setBeforeStatus(InstanceStatusEnum.RESERVED.name());
            movement.setAfterStatus(InstanceStatusEnum.AVAILABLE.name());
            LambdaUpdateWrapper<MaterialInstanceDO> update = new LambdaUpdateWrapper<MaterialInstanceDO>()
                    .eq(MaterialInstanceDO::getId, instance.getId())
                    .eq(MaterialInstanceDO::getInstanceStatus, InstanceStatusEnum.RESERVED.name())
                    .eq(MaterialInstanceDO::getReservationId, instance.getReservationId())
                    .eq(MaterialInstanceDO::getReservedByExecutionId, reqVO.getExecutionId())
                    .set(MaterialInstanceDO::getInstanceStatus, InstanceStatusEnum.AVAILABLE.name())
                    .set(MaterialInstanceDO::getReservationId, null)
                    .set(MaterialInstanceDO::getReservedByExecutionId, null)
                    .set(MaterialInstanceDO::getReservedUntil, null);
            if (materialInstanceMapper.update(null, update) != 1) {
                // 竞争失败：跳过该行，不中断整批
                continue;
            }
            movementIds.add(materialMovementService.recordMovement(movement));
            released.add(instance.getId());
        }
        return MaterialInstanceReleaseByExecutionRespVO.builder()
                .releasedInstanceIds(released)
                .movementIds(movementIds)
                .pendingInUse(pending)
                .operationId(operationId)
                .idempotentReplay(Boolean.FALSE)
                .build();
    }

    private MaterialInstanceReleaseByExecutionRespVO findReleaseByExecutionReplay(String businessIdempotentKey) {
        if (StrUtil.isBlank(businessIdempotentKey)) {
            return null;
        }
        List<MaterialMovementDO> movements = materialMovementService.listByBusinessIdempotentKey(businessIdempotentKey);
        if (CollUtil.isEmpty(movements)) {
            return null;
        }
        for (MaterialMovementDO m : movements) {
            if (!MovementTypeEnum.RELEASE.getType().equals(m.getMovementType())) {
                throw exception(MOVEMENT_IDEMPOTENT_CONFLICT, businessIdempotentKey);
            }
        }
        List<Long> released = new ArrayList<>();
        List<Long> movementIds = new ArrayList<>();
        for (MaterialMovementDO m : movements) {
            movementIds.add(m.getId());
            released.add(m.getInstanceId());
        }
        return MaterialInstanceReleaseByExecutionRespVO.builder()
                .releasedInstanceIds(released)
                .movementIds(movementIds)
                .pendingInUse(List.of())
                .operationId(movements.get(0).getOperationId())
                .idempotentReplay(Boolean.TRUE)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireReservations(LocalDateTime now, int limit) {
        LocalDateTime point = now == null ? LocalDateTime.now() : now;
        List<MaterialInstanceDO> expired = materialInstanceMapper.selectExpiredReserved(point, limit);
        int released = 0;
        for (MaterialInstanceDO instance : expired) {
            String businessKey = "expireRelease:" + instance.getReservationId() + ":" + instance.getId();
            List<MaterialMovementDO> existing = materialMovementService.listByBusinessIdempotentKey(businessKey);
            if (CollUtil.isNotEmpty(existing)) {
                continue;
            }
            MovementTraceBaseReqVO trace = new MovementTraceBaseReqVO();
            trace.setIdempotentKey(businessKey);
            trace.setBizSource("AUTO");
            trace.setOperatorType("AUTO");
            trace.setRemark("预留过期自动释放");
            String operationId = IdUtil.fastSimpleUUID();
            MaterialMovementDO movement = buildMovement(MovementTypeEnum.RELEASE.getType(), instance, null, null);
            applyTrace(movement, trace, operationId, 0);
            movement.setBeforeStatus(InstanceStatusEnum.RESERVED.name());
            movement.setAfterStatus(InstanceStatusEnum.AVAILABLE.name());
            LambdaUpdateWrapper<MaterialInstanceDO> update = new LambdaUpdateWrapper<MaterialInstanceDO>()
                    .eq(MaterialInstanceDO::getId, instance.getId())
                    .eq(MaterialInstanceDO::getInstanceStatus, InstanceStatusEnum.RESERVED.name())
                    .eq(MaterialInstanceDO::getReservationId, instance.getReservationId())
                    .eq(MaterialInstanceDO::getReservedByExecutionId, instance.getReservedByExecutionId())
                    .eq(MaterialInstanceDO::getReservedUntil, instance.getReservedUntil())
                    .set(MaterialInstanceDO::getInstanceStatus, InstanceStatusEnum.AVAILABLE.name())
                    .set(MaterialInstanceDO::getReservationId, null)
                    .set(MaterialInstanceDO::getReservedByExecutionId, null)
                    .set(MaterialInstanceDO::getReservedUntil, null);
            if (materialInstanceMapper.update(null, update) != 1) {
                continue;
            }
            try {
                materialMovementService.recordMovement(movement);
                released++;
            } catch (ServiceException ex) {
                if (Objects.equals(ex.getCode(), MOVEMENT_IDEMPOTENT_DUPLICATE.getCode())) {
                    continue;
                }
                throw ex;
            }
        }
        return released;
    }

    private void assertOwnership(MaterialInstanceDO instance, String reservationId, String executionId) {
        if (StrUtil.isBlank(instance.getReservationId())) {
            return;
        }
        if (StrUtil.isBlank(reservationId) || StrUtil.isBlank(executionId)
                || !Objects.equals(instance.getReservationId(), reservationId)
                || !Objects.equals(instance.getReservedByExecutionId(), executionId)) {
            throw exception(INSTANCE_RESERVATION_OWNER_MISMATCH, instance.getInstanceCode(),
                    reservationId, executionId);
        }
    }

    private void assertReservationNotExpired(MaterialInstanceDO instance) {
        if (instance.getReservedUntil() != null && !instance.getReservedUntil().isAfter(LocalDateTime.now())) {
            throw exception(INSTANCE_RESERVATION_EXPIRED, instance.getInstanceCode(), instance.getReservedUntil());
        }
    }

    private void assertReplayType(String businessIdempotentKey, String expectedType) {
        if (StrUtil.isBlank(businessIdempotentKey)) {
            return;
        }
        List<MaterialMovementDO> movements = materialMovementService.listByBusinessIdempotentKey(businessIdempotentKey);
        if (CollUtil.isEmpty(movements)) {
            return;
        }
        if (!expectedType.equals(movements.get(0).getMovementType())) {
            throw exception(MOVEMENT_IDEMPOTENT_CONFLICT, businessIdempotentKey);
        }
    }

    /**
     * 消耗（可允许有归属实例）。ownedPath=true 时耗尽清三列，部分保持归属。
     */
    private Long doConsumeOwned(MaterialInstanceDO instance, Integer qty, BigDecimal volUl, String afterStatus,
                                String remark, MovementTraceBaseReqVO trace, String operationId, int index,
                                boolean ownedPath) {
        if (!ownedPath && StrUtil.isNotBlank(instance.getReservationId())) {
            throw exception(INSTANCE_RESERVATION_OWNER_MISMATCH, instance.getInstanceCode(), null, null);
        }
        // 1. 状态校验
        if (!InstanceStatusEnum.isConsumable(instance.getInstanceStatus())) {
            throw exception(INSTANCE_CONSUME_STATUS_ILLEGAL, instance.getInstanceCode(), instance.getInstanceStatus());
        }
        boolean byVolume = volUl != null && volUl.compareTo(BigDecimal.ZERO) > 0;
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
        String beforeStatus = instance.getInstanceStatus();
        String finalStatus = StrUtil.blankToDefault(afterStatus, deriveStatusAfterConsume(byVolume, afterQty, afterVol));
        MaterialMovementDO movement = buildMovement(MovementTypeEnum.CONSUME.getType(), instance, null, null);
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
        LambdaUpdateWrapper<MaterialInstanceDO> update = new LambdaUpdateWrapper<MaterialInstanceDO>()
                .eq(MaterialInstanceDO::getId, instance.getId())
                .eq(MaterialInstanceDO::getInstanceStatus, beforeStatus)
                .set(MaterialInstanceDO::getInstanceStatus, finalStatus);
        if (byVolume) {
            if (beforeVol == null) {
                update.isNull(MaterialInstanceDO::getCurrentVolUl);
            } else {
                update.eq(MaterialInstanceDO::getCurrentVolUl, beforeVol);
            }
            update.set(MaterialInstanceDO::getCurrentVolUl, afterVol);
        } else if (instance.getCurrentCount() == null) {
            update.isNull(MaterialInstanceDO::getCurrentCount);
            update.set(MaterialInstanceDO::getCurrentCount, afterQty);
        } else {
            update.eq(MaterialInstanceDO::getCurrentCount, beforeQty);
            update.set(MaterialInstanceDO::getCurrentCount, afterQty);
        }
        // 耗尽或 USED：清归属；部分消耗保持归属三列
        if (InstanceStatusEnum.USED.name().equals(finalStatus)
                || InstanceStatusEnum.EXPIRED.name().equals(finalStatus)
                || InstanceStatusEnum.DISCARDED.name().equals(finalStatus)) {
            update.set(MaterialInstanceDO::getReservationId, null)
                    .set(MaterialInstanceDO::getReservedByExecutionId, null)
                    .set(MaterialInstanceDO::getReservedUntil, null);
        }
        if (materialInstanceMapper.update(null, update) != 1) {
            throw exception(INSTANCE_CONSUME_CONFLICT, instance.getInstanceCode());
        }
        return materialMovementService.recordMovement(movement);
    }



    /**
     * 普通消耗入口：拒绝有归属实例。
     */
    private Long doConsume(MaterialInstanceDO instance, Integer qty, java.math.BigDecimal volUl, String afterStatus,
                           String remark, MovementTraceBaseReqVO trace, String operationId, int index) {
        return doConsumeOwned(instance, qty, volUl, afterStatus, remark, trace, operationId, index, false);
    }

    /**
     * 消耗后的状态推导：剩余量归零 → USED，仍有剩余 → IN_USE
     */
    private String deriveStatusAfterConsume(boolean byVolume, Integer afterQty, java.math.BigDecimal afterVol) {
        if (byVolume) {
            return afterVol == null || afterVol.compareTo(java.math.BigDecimal.ZERO) <= 0
                    ? InstanceStatusEnum.USED.name() : InstanceStatusEnum.IN_USE.name();
        }
        return afterQty == null || afterQty <= 0
                ? InstanceStatusEnum.USED.name() : InstanceStatusEnum.IN_USE.name();
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
    public List<MaterialInstanceDO> getMaterialInstanceListBySampleId(String sampleId) {
        return materialInstanceMapper.selectListBySampleId(sampleId);
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
        SlotDO slot = slotMapper.selectByIdForUpdate(slotId);
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
            newStatus = SlotStatusEnum.OCCUPIED.name();
        } else if (SlotStatusEnum.OCCUPIED.name().equals(slot.getSlotStatus())) {
            newStatus = SlotStatusEnum.FREE.name();
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

    /**
     * 校验样本业务编号存在（实例绑定样本前，确保样本已登记）。
     * sampleId 为空表示试剂 / 空容器，不校验。
     */
    private void validateSampleIdIfPresent(String sampleId) {
        if (StrUtil.isBlank(sampleId)) {
            return;
        }
        if (sampleInfoMapper.selectBySampleId(sampleId) == null) {
            throw exception(SAMPLE_NOT_EXISTS);
        }
    }
}
