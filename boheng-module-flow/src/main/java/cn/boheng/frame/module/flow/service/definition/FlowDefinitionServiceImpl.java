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
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.boheng.frame.module.flow.service.definition.model.FlowMapping;
import cn.boheng.frame.module.flow.service.definition.model.FlowNodeDraft;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
 * 流程定义的草稿、发布和修订。步骤在自己的表里，通过关联挂到版本上。
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
     * 流程节点表。一条记录是流程对步骤的一次引用
     */
    @Resource
    private FlowNodeMapper flowNodeMapper;

    /**
     * 步骤表。发布时从这里取设备和动作
     */
    @Resource
    private FlowStepMapper flowStepMapper;

    /**
     * 设备契约。发布时确认设备和动作还在，不直接查设备表
     */
    @Resource
    private DeviceApi deviceApi;

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
     * 只改草稿的名称和说明。步骤关联不从这里提交。
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
     * 连步骤一起删。已发布的版本是历史，不能删。
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
     * 详情。步骤按保存时的顺序返回。
     */
    @Override
    public FlowDefinitionRespVO getDefinition(Long id) {
        FlowDefinitionDO definition = requireDefinition(id);
        FlowDefinitionRespVO resp = toResp(definition);
        List<FlowNodeDO> nodes = flowNodeMapper.selectByDefinitionId(id);
        resp.setNodeCount(nodes.size());
        resp.setNodes(nodes.stream().map(node -> toNodeResp(node, flowStepMapper.selectById(node.getStepId()))).toList());
        return resp;
    }

    /**
     * 分页，并补上每条版本的步骤数。
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
     * 关联页下拉。不带步骤正文。
     */
    @Override
    public List<FlowDefinitionRespVO> getDefinitionSimpleList() {
        return flowDefinitionMapper.selectSimpleList().stream().map(this::toResp).toList();
    }

    /**
     * 用已经关联好的步骤做发布检查。不写库。
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
     * 把已关联的步骤收成检查对象。步骤不存在或已停用单独记一条。
     */
    private List<FlowIssue> issuesOf(Long definitionId, boolean publish) {
        List<FlowNodeDO> rows = flowNodeMapper.selectByDefinitionId(definitionId);
        List<FlowIssue> issues = new ArrayList<>();
        List<FlowNodeDraft> drafts = new ArrayList<>();
        for (FlowNodeDO row : rows) {
            FlowStepDO step = row.getStepId() == null ? null : flowStepMapper.selectById(row.getStepId());
            if (step == null) {
                issues.add(new FlowIssue(row.getNodeId(), "关联的步骤不存在"));
                continue;
            }
            if (step.getStatus() != null && step.getStatus() != 0) {
                issues.add(new FlowIssue(row.getNodeId(), "步骤【" + step.getStepKey() + "】已停用"));
            }
            drafts.add(toDraft(row, step));
        }
        issues.addAll(publish ? FlowGraphChecker.checkPublish(drafts, deviceLookup()) : FlowGraphChecker.checkStructure(drafts));
        return issues;
    }

    /**
     * 发布指纹只认库里的关联和步骤。
     */
    private List<FlowNodeDraft> draftsOf(Long definitionId) {
        List<FlowNodeDraft> drafts = new ArrayList<>();
        for (FlowNodeDO row : flowNodeMapper.selectByDefinitionId(definitionId)) {
            FlowStepDO step = row.getStepId() == null ? null : flowStepMapper.selectById(row.getStepId());
            if (step != null) {
                drafts.add(toDraft(row, step));
            }
        }
        return drafts;
    }

    /**
     * 关联提供节点编码和参数映射，步骤提供名称、设备和等待。
     */
    private FlowNodeDraft toDraft(FlowNodeDO row, FlowStepDO step) {
        FlowNodeDraft draft = new FlowNodeDraft();
        draft.setNodeId(row.getNodeId());
        draft.setName(step.getName());
        draft.setStepType(FlowConstants.STEP_DEVICE_COMMAND);
        draft.setDeviceCode(StrUtil.trimToNull(step.getDeviceCode()));
        draft.setActionCode(StrUtil.trimToNull(step.getActionCode()));
        draft.setTimeoutMs(step.getTimeoutMs());
        draft.setMaxAttempts(step.getMaxAttempts());
        draft.setFailStrategy(FlowConstants.FAIL_FAST);
        draft.setMappings(parseMappings(row.getInputMapping()));
        return draft;
    }

    /**
     * 版本行转成接口对象，不含步骤。
     */
    private FlowDefinitionRespVO toResp(FlowDefinitionDO row) {
        return BeanUtils.toBean(row, FlowDefinitionRespVO.class);
    }

    /**
     * 关联行加上步骤上的设备和动作。步骤不在时名称留空。
     */
    private FlowNodeRespVO toNodeResp(FlowNodeDO row, FlowStepDO step) {
        FlowNodeRespVO resp = new FlowNodeRespVO();
        resp.setId(row.getId());
        resp.setStepId(row.getStepId());
        resp.setNodeId(row.getNodeId());
        resp.setSortNo(row.getSortNo());
        resp.setStepType(FlowConstants.STEP_DEVICE_COMMAND);
        resp.setDependsOn(parseDependsOn(row.getDependsOn()));
        resp.setFailStrategy(FlowConstants.FAIL_FAST);
        if (step != null) {
            resp.setStepKey(step.getStepKey());
            resp.setName(step.getName());
            resp.setDeviceCode(step.getDeviceCode());
            resp.setActionCode(step.getActionCode());
            resp.setTimeoutSec(step.getTimeoutMs() == null ? null : (int) (step.getTimeoutMs() / 1000));
            resp.setMaxAttempts(step.getMaxAttempts());
        }
        resp.setMappings(parseMappings(row.getInputMapping()).stream().map(mapping -> {
            FlowMappingReqVO req = new FlowMappingReqVO();
            req.setParam(mapping.getParam());
            req.setSource(mapping.getSource());
            req.setField(mapping.getField());
            req.setNodeId(mapping.getNodeId());
            req.setValue(mapping.getValue());
            return req;
        }).toList());
        return resp;
    }

    /**
     * 读依赖数组。空或坏数据当成没有前置，避免详情页打不开。
     */
    private List<String> parseDependsOn(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        return JSONUtil.toList(json, String.class);
    }

    /**
     * 读参数映射。没有映射时返回空列表。
     */
    private List<FlowMapping> parseMappings(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        return JSONUtil.toList(json, FlowMapping.class);
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

}
