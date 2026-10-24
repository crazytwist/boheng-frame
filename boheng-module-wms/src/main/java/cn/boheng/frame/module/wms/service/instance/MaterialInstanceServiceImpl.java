package cn.boheng.frame.module.wms.service.instance;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceBatchPutInReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeByContainerReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeReqVO;
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
        // 只挑可消耗状态的；默认不含 RESERVED（已被步骤预留，不能抢）
        boolean includeReserved = Boolean.TRUE.equals(reqVO.getIncludeReserved());
        List<MaterialInstanceDO> candidates = children.stream()
                .filter(c -> includeReserved
                        ? InstanceStatusEnum.isConsumable(c.getInstanceStatus())
                        : InstanceStatusEnum.AVAILABLE.name().equals(c.getInstanceStatus()))
                .sorted(positionComparator(reqVO.getStrategy()))
                .toList();
        if (candidates.size() < reqVO.getCount()) {
            throw exception(INSTANCE_CONSUME_NOT_ENOUGH_CHILD, container.getInstanceCode(),
                    reqVO.getCount(), candidates.size());
        }

        String operationId = StrUtil.blankToDefault(reqVO.getOperationId(), IdUtil.fastSimpleUUID());
        String afterStatus = StrUtil.blankToDefault(reqVO.getAfterStatus(), InstanceStatusEnum.USED.name());
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
        if (!InstanceStatusEnum.isConsumable(instance.getInstanceStatus())) {
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

        // 组装流水，但要等库存条件更新成功后再落库（幂等键按明细序号派生）
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
        // 4. 先改库存。条件里带上读到的数量 / 体积 / 状态，抢不到就整笔回滚，不能先写流水
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
        if (materialInstanceMapper.update(null, update) != 1) {
            throw exception(INSTANCE_CONSUME_CONFLICT, instance.getInstanceCode());
        }

        // 5. 库存已经改成功，再记流水。流水失败会把上面的更新一起回滚
        return materialMovementService.recordMovement(movement);
    }

    /**
     * 消耗后的状态推导：剩余量归零 → USED，仍有剩余 → IN_USE
     */
    private String deriveStatusAfterConsume(boolean byVolume, Integer afterQty, BigDecimal afterVol) {
        if (byVolume) {
            return afterVol == null || afterVol.compareTo(BigDecimal.ZERO) <= 0
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
