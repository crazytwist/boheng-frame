package cn.boheng.frame.module.flow.service.definition;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.api.DeviceApi;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowDefinitionCreateReqVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowDefinitionPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowDefinitionRespVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowDefinitionSaveReqVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowIssueRespVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowMappingReqVO;
import cn.boheng.frame.module.flow.controller.admin.definition.vo.FlowNodeRespVO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowDefinitionDO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowNodeDO;
import cn.boheng.frame.module.flow.dal.dataobject.step.FlowStepDO;
import cn.boheng.frame.module.flow.dal.mysql.definition.FlowDefinitionMapper;
import cn.boheng.frame.module.flow.dal.mysql.definition.FlowNodeMapper;
import cn.boheng.frame.module.flow.dal.mysql.step.FlowStepMapper;
import cn.boheng.frame.module.flow.engine.type.StepTypeRegistry;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.boheng.frame.module.flow.service.definition.model.FlowMapping;
import cn.boheng.frame.module.flow.service.definition.model.FlowNodeDraft;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEFINITION_DELETE_LOCKED;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEFINITION_DRAFT_EXISTS;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEFINITION_INVALID;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEFINITION_KEY_DUPLICATE;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEFINITION_LOCKED;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEFINITION_NOT_EXISTS;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEFINITION_RETIRE_STATUS;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEFINITION_REVISE_STATUS;

/**
 * 流程定义的草稿、发布和修订。节点在自己的表里，通过关联挂到版本上。
 * 发布校验走 FlowGraphChecker 四段式：结构 + 类型配置 + 图关系 + 外部引用（设备台账、子流程）。
 */
@Service
@Validated
public class FlowDefinitionServiceImpl implements FlowDefinitionService {

    /**
     * 流程版本表
     */
    @Resource
    private FlowDefinitionMapper flowDefinitionMapper;

    /**
     * 流程节点表。一条记录是流程对能力的一次引用，或一个控制类节点
     */
    @Resource
    private FlowNodeMapper flowNodeMapper;

    /**
     * 步骤表。发布时从这里取生效类型与配置
     */
    @Resource
    private FlowStepMapper flowStepMapper;

    /**
     * 设备契约。发布时确认设备和动作还在，不直接查设备表
     */
    @Resource
    private DeviceApi deviceApi;

    /**
     * 步骤类型注册表。发布时做 type_config 的类型校验
     */
    @Resource
    private StepTypeRegistry stepTypeRegistry;

    /**
     * 草稿装配器。校验与展示共用同一套类型生效规则
     */
    @Resource
    private FlowDraftAssembler draftAssembler;

    /**
     * 创建第 1 版草稿。编码一旦有过版本就不能再用。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDefinition(FlowDefinitionCreateReqVO reqVO) {
        String key = reqVO.getPipelineKey().trim();
        if (flowDefinitionMapper.selectFirstByKey(key) != null) {
            throw exception(FLOW_DEFINITION_KEY_DUPLICATE, key);
        }
        FlowDefinitionDO row = FlowDefinitionDO.builder()
                .pipelineKey(key)
                .version(1)
                .name(reqVO.getName().trim())
                .remark(StrUtil.trimToNull(reqVO.getRemark()))
                .status(FlowConstants.STATUS_DRAFT)
                .build();
        flowDefinitionMapper.insert(row);
        return row.getId();
    }

    /**
     * 只改草稿的名称和说明。节点编排不从这里提交。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDefinition(FlowDefinitionSaveReqVO reqVO) {
        FlowDefinitionDO definition = requireDraft(reqVO.getId());
        definition.setName(reqVO.getName().trim());
        definition.setRemark(StrUtil.trimToNull(reqVO.getRemark()));
        flowDefinitionMapper.updateById(definition);
    }

    /**
     * 连节点一起删。已发布的版本是历史，不能删。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDefinition(Long id) {
        FlowDefinitionDO definition = requireDefinition(id);
        if (!FlowConstants.STATUS_DRAFT.equals(definition.getStatus())) {
            throw exception(FLOW_DEFINITION_DELETE_LOCKED);
        }
        flowNodeMapper.deleteByDefinitionId(id);
        flowDefinitionMapper.deleteById(id);
    }

    /**
     * 详情。节点按保存时的顺序返回，字段为装配后的生效值。
     */
    @Override
    public FlowDefinitionRespVO getDefinition(Long id) {
        FlowDefinitionDO definition = requireDefinition(id);
        FlowDefinitionRespVO resp = toResp(definition);
        List<FlowNodeDO> nodes = flowNodeMapper.selectByDefinitionId(id);
        resp.setNodeCount(nodes.size());
        resp.setNodes(nodes.stream().map(node -> toNodeResp(node,
                node.getStepId() == null ? null : flowStepMapper.selectById(node.getStepId()))).toList());
        return resp;
    }

    /**
     * 分页，并补上每条版本的节点数。
     */
    @Override
    public PageResult<FlowDefinitionRespVO> getDefinitionPage(FlowDefinitionPageReqVO reqVO) {
        PageResult<FlowDefinitionDO> page = flowDefinitionMapper.selectPage(reqVO);
        if (CollUtil.isEmpty(page.getList())) {
            return new PageResult<>(List.of(), page.getTotal());
        }
        List<Long> ids = page.getList().stream().map(FlowDefinitionDO::getId).toList();
        Map<Long, Integer> counts = new HashMap<>();
        for (FlowNodeDO node : flowNodeMapper.selectByDefinitionIds(ids)) {
            counts.merge(node.getDefinitionId(), 1, Integer::sum);
        }
        List<FlowDefinitionRespVO> list = new ArrayList<>();
        for (FlowDefinitionDO row : page.getList()) {
            FlowDefinitionRespVO resp = toResp(row);
            resp.setNodeCount(counts.getOrDefault(row.getId(), 0));
            list.add(resp);
        }
        return new PageResult<>(list, page.getTotal());
    }

    /**
     * 关联页下拉。不带节点正文。
     */
    @Override
    public List<FlowDefinitionRespVO> getDefinitionSimpleList() {
        return flowDefinitionMapper.selectSimpleList().stream().map(this::toResp).toList();
    }

    /**
     * 用已经编排好的节点做发布检查。不写库。
     */
    @Override
    public List<FlowIssueRespVO> validateDefinition(Long id) {
        requireDraft(id);
        return issuesOf(id, true).stream()
                .map(issue -> new FlowIssueRespVO(issue.getNodeId(), issue.getMessage()))
                .toList();
    }

    /**
     * 发布当前库里的草稿。调用方应先保存。不合法则不会改成已发布。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishDefinition(Long id) {
        FlowDefinitionDO definition = requireDraft(id);
        List<FlowIssue> issues = issuesOf(id, true);
        if (CollUtil.isNotEmpty(issues)) {
            throw exception(FLOW_DEFINITION_INVALID, FlowGraphChecker.join(issues));
        }
        definition.setStatus(FlowConstants.STATUS_PUBLISHED);
        definition.setChecksum(FlowGraphChecker.checksum(draftsOf(id)));
        definition.setPublishedAt(LocalDateTime.now());
        flowDefinitionMapper.updateById(definition);
    }

    /**
     * 停用已发布版本。指纹保留，修订仍可从它复制。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retireDefinition(Long id) {
        FlowDefinitionDO definition = requireDefinition(id);
        if (!FlowConstants.STATUS_PUBLISHED.equals(definition.getStatus())) {
            throw exception(FLOW_DEFINITION_RETIRE_STATUS);
        }
        definition.setStatus(FlowConstants.STATUS_RETIRED);
        flowDefinitionMapper.updateById(definition);
    }

    /**
     * 复制出下一个草稿。同一编码已有草稿时拒绝，避免两个人各改一份。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reviseDefinition(Long id) {
        FlowDefinitionDO source = requireDefinition(id);
        if (FlowConstants.STATUS_DRAFT.equals(source.getStatus())) {
            throw exception(FLOW_DEFINITION_REVISE_STATUS);
        }
        FlowDefinitionDO open = flowDefinitionMapper.selectDraftByKey(source.getPipelineKey());
        if (open != null) {
            throw exception(FLOW_DEFINITION_DRAFT_EXISTS, source.getPipelineKey(), open.getVersion());
        }
        FlowDefinitionDO draft = FlowDefinitionDO.builder()
                .pipelineKey(source.getPipelineKey())
                .version(flowDefinitionMapper.selectMaxVersion(source.getPipelineKey()) + 1)
                .name(source.getName())
                .remark(source.getRemark())
                .status(FlowConstants.STATUS_DRAFT)
                .build();
        flowDefinitionMapper.insert(draft);
        for (FlowNodeDO node : flowNodeMapper.selectByDefinitionId(source.getId())) {
            node.setId(null);
            node.setDefinitionId(draft.getId());
            node.setCreator(null);
            node.setCreateTime(null);
            node.setUpdater(null);
            node.setUpdateTime(null);
            flowNodeMapper.insert(node);
        }
        return draft.getId();
    }

    /**
     * 按编号取版本。没有则抛不存在。
     */
    private FlowDefinitionDO requireDefinition(Long id) {
        FlowDefinitionDO definition = flowDefinitionMapper.selectById(id);
        if (definition == null) {
            throw exception(FLOW_DEFINITION_NOT_EXISTS);
        }
        return definition;
    }

    /**
     * 只有草稿能改。已发布和已停用把原因写进提示。
     */
    private FlowDefinitionDO requireDraft(Long id) {
        FlowDefinitionDO definition = requireDefinition(id);
        if (!FlowConstants.STATUS_DRAFT.equals(definition.getStatus())) {
            throw exception(FLOW_DEFINITION_LOCKED, definition.getPipelineKey(), definition.getVersion(),
                    FlowConstants.statusLabel(definition.getStatus()));
        }
        return definition;
    }

    /**
     * 收集发布校验的全部问题：能力引用断了单独记一条，
     * 其余交给 FlowGraphChecker 四段式（结构 / 类型配置 / 图关系 / 外部引用）。
     */
    private List<FlowIssue> issuesOf(Long definitionId, boolean publish) {
        List<FlowNodeDO> rows = flowNodeMapper.selectByDefinitionId(definitionId);
        List<FlowIssue> issues = new ArrayList<>();
        List<FlowNodeDraft> drafts = new ArrayList<>();
        for (FlowNodeDO row : rows) {
            // 控制类节点（loop / subflow / route / timer.wait）允许 step_id 为空——它们由 type_config 承载逻辑
            if (row.getStepId() == null && StepTypeRegistry.isControlType(row.getStepType())) {
                drafts.add(draftAssembler.toDraft(row, null));
                continue;
            }
            FlowStepDO step = row.getStepId() == null ? null : flowStepMapper.selectById(row.getStepId());
            if (step == null) {
                issues.add(new FlowIssue(row.getNodeId(), "关联的步骤不存在"));
                continue;
            }
            if (step.getStatus() != null && step.getStatus() != 0) {
                issues.add(new FlowIssue(row.getNodeId(), "步骤【" + step.getStepKey() + "】已停用"));
            }
            drafts.add(draftAssembler.toDraft(row, step));
        }
        if (publish) {
            issues.addAll(FlowGraphChecker.checkPublish(drafts, deviceLookup(), stepTypeRegistry, subflowLookup()));
        } else {
            issues.addAll(FlowGraphChecker.checkStructure(drafts));
        }
        return issues;
    }

    /**
     * 发布指纹只认库里的节点和步骤。
     */
    private List<FlowNodeDraft> draftsOf(Long definitionId) {
        List<FlowNodeDraft> drafts = new ArrayList<>();
        for (FlowNodeDO row : flowNodeMapper.selectByDefinitionId(definitionId)) {
            FlowStepDO step = row.getStepId() == null ? null : flowStepMapper.selectById(row.getStepId());
            drafts.add(draftAssembler.toDraft(row, step));
        }
        return drafts;
    }

    /**
     * 版本行转成接口对象，不含节点。
     */
    private FlowDefinitionRespVO toResp(FlowDefinitionDO row) {
        return BeanUtils.toBean(row, FlowDefinitionRespVO.class);
    }

    /**
     * 节点行转成接口对象。生效值经装配器解析，与发布校验看到的是同一张图。
     */
    private FlowNodeRespVO toNodeResp(FlowNodeDO row, FlowStepDO step) {
        FlowNodeDraft draft = draftAssembler.toDraft(row, step);
        FlowNodeRespVO resp = new FlowNodeRespVO();
        resp.setId(row.getId());
        resp.setStepId(row.getStepId());
        resp.setNodeId(row.getNodeId());
        resp.setSortNo(row.getSortNo());
        resp.setName(draft.getName());
        resp.setStepType(draft.getStepType());
        resp.setTypeConfig(draft.getTypeConfig());
        resp.setDependsOn(draft.getDependsOn());
        resp.setJoinPolicy(draft.getJoinPolicy());
        resp.setJoinFailurePolicy(draft.getJoinFailurePolicy());
        resp.setBranches(draft.getBranches());
        resp.setTimeoutSec(draft.getTimeoutMs() == null ? null : (int) (draft.getTimeoutMs() / 1000));
        resp.setMaxAttempts(draft.getMaxAttempts());
        resp.setFailStrategy(draft.getFailStrategy());
        resp.setMappings(draft.getMappings().stream().map(mapping -> {
            FlowMappingReqVO req = new FlowMappingReqVO();
            req.setParam(mapping.getParam());
            req.setSource(mapping.getSource());
            req.setField(mapping.getField());
            req.setNodeId(mapping.getNodeId());
            req.setValue(mapping.getValue());
            return req;
        }).toList());
        if (step != null) {
            resp.setStepKey(step.getStepKey());
        }
        return resp;
    }

    /**
     * 把设备契约收成检查器要的两个问题。
     */
    private FlowGraphChecker.DeviceLookup deviceLookup() {
        return new FlowGraphChecker.DeviceLookup() {

            /**
             * 台账里没有这台设备时返回 null。
             */
            @Override
            public cn.boheng.frame.module.device.api.dto.DeviceSummaryDTO getByCode(String deviceCode) {
                return deviceApi.getDeviceByCode(deviceCode);
            }

            /**
             * 动作停用或不存在时返回 false。
             */
            @Override
            public boolean hasEnabledAction(String deviceTypeCode, String actionCode) {
                return deviceApi.hasEnabledAction(deviceTypeCode, actionCode);
            }
        };
    }

    /**
     * 子流程引用检查：目标编码（或固定版本）必须处于已发布状态。
     */
    private FlowGraphChecker.SubflowLookup subflowLookup() {
        return (pipelineKey, version) -> {
            FlowDefinitionDO target = version == null
                    ? flowDefinitionMapper.selectPublishedLatest(pipelineKey)
                    : flowDefinitionMapper.selectByKeyAndVersion(pipelineKey, version);
            return target != null && FlowConstants.STATUS_PUBLISHED.equals(target.getStatus());
        };
    }

}
