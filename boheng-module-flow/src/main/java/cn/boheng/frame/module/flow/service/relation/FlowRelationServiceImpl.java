package cn.boheng.frame.module.flow.service.relation;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationRespVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationSaveReqVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationUpdateReqVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationLayoutReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowDefinitionDO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowNodeDO;
import cn.boheng.frame.module.flow.dal.dataobject.step.FlowStepDO;
import cn.boheng.frame.module.flow.dal.mysql.definition.FlowDefinitionMapper;
import cn.boheng.frame.module.flow.dal.mysql.definition.FlowNodeMapper;
import cn.boheng.frame.module.flow.dal.mysql.step.FlowStepMapper;
import cn.boheng.frame.module.flow.engine.type.StepTypeRegistry;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.service.definition.FlowDraftAssembler;
import cn.boheng.frame.module.flow.service.definition.FlowGraphChecker;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.boheng.frame.module.flow.service.definition.model.FlowNodeDraft;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEFINITION_INVALID;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEFINITION_LOCKED;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEFINITION_NOT_EXISTS;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_RELATION_LIMIT;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_RELATION_NODE_DUPLICATE;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_RELATION_NODE_INVALID;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_RELATION_NOT_EXISTS;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_RELATION_STEP_DISABLED;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_STEP_CONTROL_TYPE;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_STEP_NOT_EXISTS;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_STEP_TYPE_CONFIG_INVALID;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_STEP_TYPE_NOT_REGISTERED;

/**
 * 流程版本里的节点编排。v3 放开 depends_on 自由连线后，
 * 「画得出跑不了」的防线是每次变更后立刻做结构校验（前置存在、无环、join 一致），
 * 草稿在任何时刻都保持结构合法。sortNo 只管展示顺序，不再是依赖的真相源。
 */
@Service
@Validated
public class FlowRelationServiceImpl implements FlowRelationService {

    /**
     * 节点编码和步骤编码用同一条规则，这样默认可以直接拿步骤编码当节点编码。
     */
    private static final Pattern NODE_ID = Pattern.compile(FlowConstants.NODE_ID_REGEX);

    /** node_id 列宽 32，自动加后缀时不能超 */
    private static final int NODE_ID_MAX_LENGTH = 32;

    /**
     * 关联表
     */
    @Resource
    private FlowNodeMapper flowNodeMapper;

    /**
     * 流程版本
     */
    @Resource
    private FlowDefinitionMapper flowDefinitionMapper;

    /**
     * 步骤表
     */
    @Resource
    private FlowStepMapper flowStepMapper;

    /**
     * 步骤类型注册表。控制类节点创建与配置更新时校验
     */
    @Resource
    private StepTypeRegistry stepTypeRegistry;

    /**
     * 草稿装配器。结构校验和列表展示共用同一套生效规则
     */
    @Resource
    private FlowDraftAssembler draftAssembler;

    /**
     * 加节点。挂能力走步骤表取类型配置；控制类节点直接带类型和配置。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRelation(FlowRelationSaveReqVO reqVO) {
        FlowDefinitionDO definition = requireDraft(reqVO.getDefinitionId());
        List<FlowNodeDO> nodes = new ArrayList<>(flowNodeMapper.selectByDefinitionId(definition.getId()));
        if (nodes.size() >= FlowConstants.MAX_NODES) {
            throw exception(FLOW_RELATION_LIMIT, FlowConstants.MAX_NODES);
        }
        FlowNodeDO.FlowNodeDOBuilder builder;
        if (reqVO.getStepId() != null) {
            // 挂能力：类型与配置取自步骤表
            FlowStepDO step = flowStepMapper.selectById(reqVO.getStepId());
            if (step == null) {
                throw exception(FLOW_STEP_NOT_EXISTS);
            }
            if (step.getStatus() != null && step.getStatus() != 0) {
                throw exception(FLOW_RELATION_STEP_DISABLED, step.getStepKey());
            }
            String nodeId = nextNodeId(nodes, step.getStepKey());
            builder = prepareInsert(definition, nodes, nodeId)
                    .stepId(step.getId());
        } else {
            // 控制类节点：类型 + 配置 + 名称都由请求给出
            String stepType = StrUtil.trimToEmpty(reqVO.getStepType());
            if (StrUtil.isBlank(stepType) || !stepTypeRegistry.isRegistered(stepType)) {
                throw exception(FLOW_STEP_TYPE_NOT_REGISTERED, stepType);
            }
            if (!StepTypeRegistry.isControlType(stepType)) {
                // 能力型类型必须挂步骤，不允许在节点上凭空写配置
                throw exception(FLOW_STEP_CONTROL_TYPE, stepType);
            }
            if (StrUtil.isBlank(reqVO.getName())) {
                throw exception(FLOW_DEFINITION_INVALID, "控制类节点要填名称");
            }
            validateTypeConfig(stepType, reqVO.getTypeConfig(), reqVO.getName());
            if (StrUtil.isBlank(reqVO.getNodeId())) {
                throw exception(FLOW_RELATION_NODE_INVALID);
            }
            builder = prepareInsert(definition, nodes, reqVO.getNodeId().trim())
                    .name(reqVO.getName().trim())
                    .stepType(stepType)
                    .typeConfig(StrUtil.trimToNull(reqVO.getTypeConfig()));
        }
        FlowNodeDO row = builder
                .sortNo(nodes.size())
                .dependsOn(toJson(reqVO.getDependsOn()))
                .x(reqVO.getX())
                .y(reqVO.getY())
                .build();
        flowNodeMapper.insert(row);
        assertStructure(definition.getId());
        return row.getId();
    }

    /**
     * 改编排形态：前置、汇合、分支出口、参数映射；控制类节点还能改名称和配置。
     * 挂能力的节点可写节点级 typeConfig 覆盖。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRelation(FlowRelationUpdateReqVO reqVO) {
        FlowNodeDO row = requireRelation(reqVO.getId());
        requireDraft(row.getDefinitionId());
        boolean control = row.getStepId() == null;
        // 名称：控制类节点可改；挂能力时名称跟随步骤，忽略
        if (control && reqVO.getName() != null) {
            if (StrUtil.isBlank(reqVO.getName())) {
                throw exception(FLOW_DEFINITION_INVALID, "控制类节点要填名称");
            }
            row.setName(reqVO.getName().trim());
        }
        // 类型配置：控制类节点必填；挂能力时为节点级覆盖
        if (reqVO.getTypeConfig() != null) {
            String effectiveType = StrUtil.isNotBlank(row.getStepType()) ? row.getStepType()
                    : effectiveStepTypeOf(row);
            validateTypeConfig(effectiveType, reqVO.getTypeConfig(), row.getNodeId());
            row.setTypeConfig(StrUtil.trimToNull(reqVO.getTypeConfig()));
        }
        // 前置：允许清空成起点
        if (reqVO.getDependsOn() != null) {
            if (reqVO.getDependsOn().contains(row.getNodeId())) {
                throw exception(FLOW_DEFINITION_INVALID, "节点不能依赖自己");
            }
            row.setDependsOn(toJson(reqVO.getDependsOn()));
        }
        // 汇合策略：空串表示清掉回落默认
        if (reqVO.getJoinPolicy() != null) {
            row.setJoinPolicy(StrUtil.trimToNull(reqVO.getJoinPolicy()));
        }
        if (reqVO.getJoinFailurePolicy() != null) {
            row.setJoinFailurePolicy(StrUtil.trimToNull(reqVO.getJoinFailurePolicy()));
        }
        // 出口映射：只有 route 节点有意义
        if (reqVO.getBranches() != null) {
            boolean route = FlowConstants.STEP_ROUTE.equals(StrUtil.isNotBlank(row.getStepType())
                    ? row.getStepType() : effectiveStepTypeOf(row));
            row.setBranches(route && !reqVO.getBranches().isEmpty()
                    ? JSONUtil.toJsonStr(reqVO.getBranches()) : null);
        }
        // 参数映射
        if (reqVO.getMappings() != null) {
            row.setInputMapping(reqVO.getMappings().isEmpty() ? null
                    : JSONUtil.toJsonStr(reqVO.getMappings()));
        }
        // 输出映射：{输出字段: 上下文键}，空表清空
        if (reqVO.getOutputMapping() != null) {
            row.setOutputMapping(reqVO.getOutputMapping().isEmpty() ? null
                    : JSONUtil.toJsonStr(reqVO.getOutputMapping()));
        }
        // 画布坐标
        if (reqVO.getX() != null) {
            row.setX(reqVO.getX());
        }
        if (reqVO.getY() != null) {
            row.setY(reqVO.getY());
        }
        flowNodeMapper.updateById(row);
        assertStructure(row.getDefinitionId());
    }

    /**
     * 批量保存画布坐标。位置不改变图结构，跳过结构校验，纯坐标落库。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLayout(FlowRelationLayoutReqVO reqVO) {
        for (FlowRelationLayoutReqVO.Item item : reqVO.getItems()) {
            FlowNodeDO row = requireRelation(item.getId());
            row.setX(item.getX());
            row.setY(item.getY());
            flowNodeMapper.updateById(row);
        }
    }

    /**
     * 卸下节点。还有节点依赖它时先解除那些依赖。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRelation(Long id) {
        FlowNodeDO row = requireRelation(id);
        requireDraft(row.getDefinitionId());
        flowNodeMapper.deleteById(id);
        List<FlowNodeDO> nodes = new ArrayList<>(flowNodeMapper.selectByDefinitionId(row.getDefinitionId()));
        reorder(nodes);
        assertStructure(row.getDefinitionId());
    }

    /**
     * 只调展示顺序，不动依赖。已经在两端时不动。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void moveRelation(Long id, int offset) {
        if (offset != -1 && offset != 1) {
            return;
        }
        FlowNodeDO row = requireRelation(id);
        requireDraft(row.getDefinitionId());
        List<FlowNodeDO> nodes = new ArrayList<>(flowNodeMapper.selectByDefinitionId(row.getDefinitionId()));
        int index = -1;
        for (int i = 0; i < nodes.size(); i++) {
            if (id.equals(nodes.get(i).getId())) {
                index = i;
                break;
            }
        }
        int target = index + offset;
        if (index < 0 || target < 0 || target >= nodes.size()) {
            return;
        }
        FlowNodeDO moving = nodes.remove(index);
        nodes.add(target, moving);
        reorder(nodes);
    }

    /**
     * 分页。展示值经装配器解析成生效值。
     */
    @Override
    public PageResult<FlowRelationRespVO> getRelationPage(FlowRelationPageReqVO reqVO) {
        PageResult<FlowNodeDO> page = flowNodeMapper.selectPage(reqVO);
        if (CollUtil.isEmpty(page.getList())) {
            return new PageResult<>(List.of(), page.getTotal());
        }
        Map<Long, FlowDefinitionDO> definitions = new HashMap<>();
        Map<Long, FlowStepDO> steps = new HashMap<>();
        List<FlowRelationRespVO> list = new ArrayList<>();
        for (FlowNodeDO row : page.getList()) {
            FlowDefinitionDO definition = definitions.computeIfAbsent(row.getDefinitionId(), flowDefinitionMapper::selectById);
            FlowStepDO step = row.getStepId() == null ? null : steps.computeIfAbsent(row.getStepId(), flowStepMapper::selectById);
            list.add(toResp(row, definition, step));
        }
        return new PageResult<>(list, page.getTotal());
    }

    // ==================== 内部 ====================

/**
     * 挂能力时自动分配节点编码。默认取步骤编码，同版本内被占用时依次试 _2 / _3 后缀。
     * 节点编码一律由系统分配，不接受调用方指定：同一版本挂两次同一能力是常态，
     * 自动去重才不用人惦记这件事。前端只读展示这个值，规则只有这一处。
     */
    private String nextNodeId(List<FlowNodeDO> nodes, String stepKey) {
        Set<String> taken = new HashSet<>();
        for (FlowNodeDO item : nodes) {
            taken.add(item.getNodeId());
        }
        if (!taken.contains(stepKey)) {
            return stepKey;
        }
        for (int i = 2; i <= FlowConstants.MAX_NODES + 1; i++) {
            String suffix = "_" + i;
            // 节点编码最长 32 位（与 node_id 列宽一致），后缀挤进来时先把前缀截到够长
            String base = stepKey.length() + suffix.length() > NODE_ID_MAX_LENGTH
                    ? stepKey.substring(0, NODE_ID_MAX_LENGTH - suffix.length()) : stepKey;
            String candidate = base + suffix;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        throw exception(FLOW_RELATION_LIMIT, FlowConstants.MAX_NODES);
    }

    /**
     * 新节点插入前的公共检查：编码合法且不与版本内已有节点重复
     */
    private FlowNodeDO.FlowNodeDOBuilder prepareInsert(FlowDefinitionDO definition,
                                                       List<FlowNodeDO> nodes, String nodeId) {
        if (!NODE_ID.matcher(nodeId).matches()) {
            throw exception(FLOW_RELATION_NODE_INVALID);
        }
        if (nodes.stream().anyMatch(item -> nodeId.equals(item.getNodeId()))) {
            throw exception(FLOW_RELATION_NODE_DUPLICATE, nodeId);
        }
        return FlowNodeDO.builder().definitionId(definition.getId()).nodeId(nodeId);
    }

    /**
     * 类型配置必须能被该类型的配置模型解析并通过类型校验
     */
    private void validateTypeConfig(String stepType, String typeConfig, String label) {
        Object config = stepTypeRegistry.parseConfig(stepType, typeConfig);
        List<FlowIssue> issues = new ArrayList<>();
        stepTypeRegistry.getDescriptor(stepType).validate(config, label, issues);
        if (!issues.isEmpty()) {
            throw exception(FLOW_STEP_TYPE_CONFIG_INVALID, label, FlowGraphChecker.join(issues));
        }
    }

    /**
     * 节点引用能力时的生效类型（无节点级覆盖时取步骤表）
     */
    private String effectiveStepTypeOf(FlowNodeDO row) {
        if (StrUtil.isNotBlank(row.getStepType()) || row.getStepId() == null) {
            return row.getStepType();
        }
        FlowStepDO step = flowStepMapper.selectById(row.getStepId());
        return step == null ? null : step.getStepType();
    }

    /**
     * 每次变更后立刻校验结构。不合法抛错回滚，草稿永远结构合法。
     */
    private void assertStructure(Long definitionId) {
        List<FlowIssue> issues = FlowGraphChecker.checkStructure(draftAssembler.assemble(definitionId));
        if (!issues.isEmpty()) {
            throw exception(FLOW_DEFINITION_INVALID, FlowGraphChecker.join(issues));
        }
    }

    /**
     * 重排展示顺序。v3 顺序只影响列表展示，依赖关系不动。
     */
    private void reorder(List<FlowNodeDO> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            FlowNodeDO row = nodes.get(i);
            if (row.getSortNo() == null || row.getSortNo() != i) {
                row.setSortNo(i);
                flowNodeMapper.updateById(row);
            }
        }
    }

    /**
     * 关联不存在时抛错。
     */
    private FlowNodeDO requireRelation(Long id) {
        FlowNodeDO row = flowNodeMapper.selectById(id);
        if (row == null) {
            throw exception(FLOW_RELATION_NOT_EXISTS);
        }
        return row;
    }

    /**
     * 只有草稿能改关联。
     */
    private FlowDefinitionDO requireDraft(Long id) {
        FlowDefinitionDO definition = flowDefinitionMapper.selectById(id);
        if (definition == null) {
            throw exception(FLOW_DEFINITION_NOT_EXISTS);
        }
        if (!FlowConstants.STATUS_DRAFT.equals(definition.getStatus())) {
            throw exception(FLOW_DEFINITION_LOCKED, definition.getPipelineKey(), definition.getVersion(),
                    FlowConstants.statusLabel(definition.getStatus()));
        }
        return definition;
    }

    /**
     * 列表行。生效值用装配器解析，展示和校验看到的是同一张图。
     */
    private FlowRelationRespVO toResp(FlowNodeDO row, FlowDefinitionDO definition, FlowStepDO step) {
        FlowRelationRespVO resp = new FlowRelationRespVO();
        resp.setId(row.getId());
        resp.setDefinitionId(row.getDefinitionId());
        resp.setSortNo(row.getSortNo());
        resp.setNodeId(row.getNodeId());
        resp.setStepId(row.getStepId());
        if (definition != null) {
            resp.setPipelineKey(definition.getPipelineKey());
            resp.setDefinitionName(definition.getName());
            resp.setVersion(definition.getVersion());
            resp.setDefinitionStatus(definition.getStatus());
        }
        FlowNodeDraft draft = draftAssembler.toDraft(row, step);
        resp.setName(draft.getName());
        resp.setStepType(draft.getStepType());
        resp.setTypeConfig(draft.getTypeConfig());
        resp.setDependsOn(draft.getDependsOn());
        resp.setJoinPolicy(draft.getJoinPolicy());
        resp.setJoinFailurePolicy(draft.getJoinFailurePolicy());
        resp.setBranches(draft.getBranches());
        resp.setOutputMapping(row.getOutputMapping());
        resp.setOutputSchema(draft.getOutputSchema());
        resp.setMappings(draft.getMappings().stream().map(mapping -> {
            cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowMappingReqVO req =
                    new cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowMappingReqVO();
            req.setParam(mapping.getParam());
            req.setSource(mapping.getSource());
            req.setField(mapping.getField());
            req.setNodeId(mapping.getNodeId());
            req.setValue(mapping.getValue());
            return req;
        }).toList());
        resp.setTimeoutSec(FlowConstants.toTimeoutSec(draft.getTimeoutMs()));
        resp.setMaxAttempts(draft.getMaxAttempts());
        resp.setX(row.getX());
        resp.setY(row.getY());
        if (step != null) {
            resp.setStepKey(step.getStepKey());
            resp.setStepName(step.getName());
        }
        return resp;
    }

    /**
     * 依赖数组收成 JSON。空列表存 "[]"。
     */
    private String toJson(List<String> dependsOn) {
        return JSONUtil.toJsonStr(dependsOn == null ? List.of() : dependsOn);
    }

}
