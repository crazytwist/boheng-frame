package cn.boheng.frame.module.flow.service.definition;

import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowNodeDO;
import cn.boheng.frame.module.flow.dal.dataobject.step.FlowStepDO;
import cn.boheng.frame.module.flow.dal.mysql.definition.FlowNodeMapper;
import cn.boheng.frame.module.flow.dal.mysql.step.FlowStepMapper;
import cn.boheng.frame.module.flow.engine.type.config.LoopConfig;
import cn.boheng.frame.module.flow.engine.type.config.SubflowConfig;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.service.definition.model.FlowMapping;
import cn.boheng.frame.module.flow.service.definition.model.FlowNodeDraft;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把一个流程版本的节点行 + 能力行装配成 FlowNodeDraft 列表。
 * 草稿装配是「类型生效规则」的唯一实现：
 * 节点级覆盖优先（step_type / type_config / runConfig），引用能力时回落到步骤表。
 * 定义服务的发布校验与关联服务的结构校验共用这里，保证两边看到的图完全一致。
 */
@Component
public class FlowDraftAssembler {

    /**
     * 节点表
     */
    @Resource
    private FlowNodeMapper flowNodeMapper;

    /**
     * 步骤表
     */
    @Resource
    private FlowStepMapper flowStepMapper;

    /**
     * 按版本装配全部节点。能力行缺失的节点照常返回（由校验器记问题）
     */
    public List<FlowNodeDraft> assemble(Long definitionId) {
        List<FlowNodeDraft> drafts = new ArrayList<>();
        for (FlowNodeDO row : flowNodeMapper.selectByDefinitionId(definitionId)) {
            FlowStepDO step = row.getStepId() == null ? null : flowStepMapper.selectById(row.getStepId());
            drafts.add(toDraft(row, step));
        }
        return drafts;
    }

    /**
     * 单节点装配。类型生效规则：节点级覆盖 &gt; 步骤表
     */
    public FlowNodeDraft toDraft(FlowNodeDO row, FlowStepDO step) {
        FlowNodeDraft draft = new FlowNodeDraft();
        draft.setNodeId(row.getNodeId());
        draft.setStepId(row.getStepId());
        // 名称：控制类节点用节点上的；挂能力用步骤上的
        draft.setName(StrUtil.isNotBlank(row.getName()) ? row.getName()
                : step == null ? null : step.getName());
        // 类型与配置：节点级覆盖优先
        draft.setStepType(StrUtil.isNotBlank(row.getStepType()) ? row.getStepType()
                : step == null ? null : step.getStepType());
        draft.setTypeConfig(StrUtil.isNotBlank(row.getTypeConfig()) ? row.getTypeConfig()
                : step == null ? null : step.getTypeConfig());
        // 图形态
        draft.setDependsOn(parseDependsOn(row.getDependsOn()));
        draft.setJoinPolicy(row.getJoinPolicy());
        draft.setJoinFailurePolicy(row.getJoinFailurePolicy());
        draft.setBranches(parseBranches(row.getBranches()));
        draft.setMappings(parseMappings(row.getInputMapping()));
        // 运行覆盖：超时 / 次数 / 失败策略
        JSONObject run = parseObject(row.getRunConfig());
        Long timeoutMs = run == null ? null : run.getLong("timeoutMs");
        Integer maxAttempts = run == null ? null : run.getInt("maxAttempts");
        String failStrategy = run == null ? null : run.getStr("failureStrategy");
        draft.setTimeoutMs(timeoutMs != null ? timeoutMs
                : step == null ? null : step.getTimeoutMs());
        draft.setMaxAttempts(maxAttempts != null ? maxAttempts
                : step == null ? null : step.getMaxAttempts());
        draft.setFailStrategy(StrUtil.isNotBlank(failStrategy) ? failStrategy : FlowConstants.FAIL_FAST);
        // 控制类型的图校验辅助字段
        fillControlFields(draft);
        return draft;
    }

    /**
     * loop / subflow 的区间与目标从 type_config 里展开，供图校验用
     */
    private void fillControlFields(FlowNodeDraft draft) {
        if (FlowConstants.STEP_LOOP.equals(draft.getStepType()) && draft.getTypeConfig() != null) {
            LoopConfig loop = JSONUtil.toBean(draft.getTypeConfig(), LoopConfig.class);
            draft.setLoopBodyEntry(loop == null ? null : loop.getBodyEntryNodeId());
            draft.setLoopBodyExit(loop == null ? null : loop.getBodyExitNodeId());
        }
        if (FlowConstants.STEP_SUBFLOW.equals(draft.getStepType()) && draft.getTypeConfig() != null) {
            SubflowConfig subflow = JSONUtil.toBean(draft.getTypeConfig(), SubflowConfig.class);
            if (subflow != null && subflow.getDispatchMode() != null
                    && "SINGLE".equals(subflow.getDispatchMode())) {
                draft.setSubflowTargetKey(subflow.getPipelineKey());
            }
        }
    }

    /**
     * 读依赖数组。坏数据当成没有前置，避免详情页打不开
     */
    private List<String> parseDependsOn(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(json, String.class);
        } catch (RuntimeException ex) {
            return new ArrayList<>();
        }
    }

    /**
     * 读出口映射：出口名 -&gt; 目标列表
     */
    private Map<String, List<String>> parseBranches(String json) {
        if (StrUtil.isBlank(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, List<String>> branches = new LinkedHashMap<>();
            JSONUtil.parseObj(json).forEach((key, value) -> branches.put(key,
                    JSONUtil.toList(value == null ? "[]" : value.toString(), String.class)));
            return branches;
        } catch (RuntimeException ex) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 读参数映射
     */
    private List<FlowMapping> parseMappings(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(json, FlowMapping.class);
        } catch (RuntimeException ex) {
            return new ArrayList<>();
        }
    }

    /**
     * 读 JSON 对象。空或坏数据返回 null
     */
    private JSONObject parseObject(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (RuntimeException ex) {
            return null;
        }
    }

}
