package cn.boheng.frame.module.flow.service.relation;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationRespVO;
import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationSaveReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowDefinitionDO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowNodeDO;
import cn.boheng.frame.module.flow.dal.dataobject.step.FlowStepDO;
import cn.boheng.frame.module.flow.dal.mysql.definition.FlowDefinitionMapper;
import cn.boheng.frame.module.flow.dal.mysql.definition.FlowNodeMapper;
import cn.boheng.frame.module.flow.dal.mysql.step.FlowStepMapper;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEFINITION_LOCKED;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_DEFINITION_NOT_EXISTS;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_RELATION_LIMIT;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_RELATION_NODE_DUPLICATE;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_RELATION_NODE_INVALID;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_RELATION_NOT_EXISTS;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_RELATION_STEP_DISABLED;
import static cn.boheng.frame.module.flow.enums.ErrorCodeConstants.FLOW_STEP_NOT_EXISTS;

/**
 * 关联只改草稿。顺序变化后，每一步的前置都按新的相邻关系重写。
 */
@Service
@Validated
public class FlowRelationServiceImpl implements FlowRelationService {

    /**
     * 节点编码和步骤编码用同一条规则，这样默认可以直接拿步骤编码当节点编码。
     */
    private static final Pattern NODE_ID = Pattern.compile(FlowConstants.NODE_ID_REGEX);

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
     * 挂到草稿末尾。节点编码空着时用步骤编码。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRelation(FlowRelationSaveReqVO reqVO) {
        FlowDefinitionDO definition = requireDraft(reqVO.getDefinitionId());
        FlowStepDO step = flowStepMapper.selectById(reqVO.getStepId());
        if (step == null) {
            throw exception(FLOW_STEP_NOT_EXISTS);
        }
        if (step.getStatus() != null && step.getStatus() != 0) {
            throw exception(FLOW_RELATION_STEP_DISABLED, step.getStepKey());
        }
        List<FlowNodeDO> nodes = new ArrayList<>(flowNodeMapper.selectByDefinitionId(definition.getId()));
        if (nodes.size() >= FlowConstants.MAX_NODES) {
            throw exception(FLOW_RELATION_LIMIT, FlowConstants.MAX_NODES);
        }
        String nodeId = StrUtil.blankToDefault(StrUtil.trim(reqVO.getNodeId()), step.getStepKey());
        if (!NODE_ID.matcher(nodeId).matches()) {
            throw exception(FLOW_RELATION_NODE_INVALID);
        }
        if (nodes.stream().anyMatch(item -> nodeId.equals(item.getNodeId()))) {
            throw exception(FLOW_RELATION_NODE_DUPLICATE, nodeId);
        }
        FlowNodeDO row = FlowNodeDO.builder()
                .definitionId(definition.getId())
                .stepId(step.getId())
                .nodeId(nodeId)
                .sortNo(nodes.size())
                .dependsOn("[]")
                .build();
        flowNodeMapper.insert(row);
        nodes.add(row);
        rewriteOrder(nodes);
        return row.getId();
    }

    /**
     * 卸下关联。步骤定义留在步骤表里。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRelation(Long id) {
        FlowNodeDO row = requireRelation(id);
        requireDraft(row.getDefinitionId());
        flowNodeMapper.deleteById(id);
        List<FlowNodeDO> nodes = new ArrayList<>(flowNodeMapper.selectByDefinitionId(row.getDefinitionId()));
        rewriteOrder(nodes);
    }

    /**
     * 和相邻的一条交换位置。已经在两端时不动。
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
        rewriteOrder(nodes);
    }

    /**
     * 分页，并补上流程名称和步骤上的设备动作。
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

    /**
     * 按当前列表重写顺序和前置。第一步没有前置，后面每步只依赖紧挨着的上一步。
     */
    private void rewriteOrder(List<FlowNodeDO> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            FlowNodeDO row = nodes.get(i);
            List<String> dependsOn = i == 0 ? List.of() : List.of(nodes.get(i - 1).getNodeId());
            row.setSortNo(i);
            row.setDependsOn(JSONUtil.toJsonStr(dependsOn));
            flowNodeMapper.updateById(row);
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
     * 列表行。步骤被删掉时设备动作为空，页面上能看出来断了。
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
        if (step != null) {
            resp.setStepKey(step.getStepKey());
            resp.setStepName(step.getName());
            resp.setDeviceCode(step.getDeviceCode());
            resp.setActionCode(step.getActionCode());
            resp.setTimeoutSec(step.getTimeoutMs() == null ? null : (int) (step.getTimeoutMs() / 1000));
            resp.setMaxAttempts(step.getMaxAttempts());
        }
        return resp;
    }

}
