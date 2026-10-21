package cn.boheng.frame.module.flow.service.task;

import cn.boheng.frame.module.flow.controller.app.task.vo.ExternalTaskDomainRespVO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowNodeDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskDO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowExecutionDO;
import cn.boheng.frame.module.flow.dal.mysql.definition.FlowNodeMapper;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowExecutionMapper;
import cn.boheng.frame.module.flow.enums.external.ExternalAreaEnum;
import cn.boheng.frame.module.flow.enums.external.ExternalTaskStatusEnum;
import cn.boheng.frame.module.wms.api.MaterialInstanceExternalApi;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 清华 {@code TsinghuaTaskServiceImpl#listTaskByArea} 数据组装（数据源为 flow_device_task）。
 */
@Service
public class FlowDeviceTaskExternalServiceImpl implements FlowDeviceTaskExternalService {

    @Resource
    private FlowDeviceTaskService deviceTaskService;
    @Resource
    private FlowExecutionMapper flowExecutionMapper;
    @Resource
    private FlowNodeMapper flowNodeMapper;
    @Resource
    private MaterialInstanceExternalApi materialInstanceExternalApi;
    @Resource
    private FlowDeviceTaskParamEnhancer paramEnhancer;

    @Override
    public ExternalTaskDomainRespVO listFirstExecutableByArea(String area) {
        List<FlowDeviceTaskDO> tasks = deviceTaskService.listPendingByArea(area);
        if (CollUtil.isEmpty(tasks)) {
            return null;
        }
        return assemble(tasks.get(0));
    }

    private ExternalTaskDomainRespVO assemble(FlowDeviceTaskDO task) {
        ExternalTaskDomainRespVO domain = new ExternalTaskDomainRespVO();
        domain.setTaskId(FlowDeviceTaskExternalIds.toExternalTaskId(task));

        JSONObject params = parseParams(task);
        domain.setBatchId(resolveBatchId(params, task));

        Integer taskSeq = resolveTaskSequence(task, params);
        domain.setTaskType(taskSeq);
        domain.setName(taskSeq != null ? String.valueOf(taskSeq) : null);
        domain.setStatus(ExternalTaskStatusEnum.SS_Idle.getCode());

        domain.setConfig(resolveConfig(params, task));
        buildDestinationAndLabware(domain, task, params);
        paramEnhancer.enhance(task, domain);
        return domain;
    }

    /**
     * 下发给设备的步骤序号：优先 params，否则取当前节点 {@link FlowNodeDO#getSortNo()}。
     */
    private Integer resolveTaskSequence(FlowDeviceTaskDO task, JSONObject params) {
        Integer fromParams = params.getInt("taskSeq");
        if (fromParams == null) {
            fromParams = params.getInt("seqNo");
        }
        if (fromParams == null) {
            fromParams = Convert.toInt(params.get("序号"));
        }
        if (fromParams != null) {
            return fromParams;
        }
        FlowNodeDO currentNode = findCurrentNode(task);
        return currentNode != null ? currentNode.getSortNo() : null;
    }

    private FlowNodeDO findCurrentNode(FlowDeviceTaskDO task) {
        if (task == null || task.getExecutionId() == null || StrUtil.isBlank(task.getNodeId())) {
            return null;
        }
        FlowExecutionDO execution = flowExecutionMapper.selectById(task.getExecutionId());
        if (execution == null || execution.getDefinitionId() == null) {
            return null;
        }
        return flowNodeMapper.selectByDefinitionIdAndNodeId(execution.getDefinitionId(), task.getNodeId());
    }

    private Integer resolveBatchId(JSONObject params, FlowDeviceTaskDO task) {
        Integer workOrderId = params.getInt("workOrderId");
        if (workOrderId != null) {
            return workOrderId;
        }
        workOrderId = Convert.toInt(params.get("batchId"));
        if (workOrderId != null) {
            return workOrderId;
        }
        FlowExecutionDO execution = flowExecutionMapper.selectById(task.getExecutionId());
        if (execution != null && StrUtil.isNotBlank(execution.getBusinessKey())) {
            Integer fromKey = Convert.toInt(execution.getBusinessKey());
            if (fromKey != null) {
                return fromKey;
            }
        }
        return null;
    }

    private Object resolveConfig(JSONObject params, FlowDeviceTaskDO task) {
        Object fromSampleSeq = firstSampleConfig(params);
        if (fromSampleSeq != null) {
            return fromSampleSeq;
        }
        if (params.containsKey("config") && params.get("config") != null) {
            return params.get("config");
        }
        if (StrUtil.isNotBlank(task.getParamsJson()) && !params.isEmpty()) {
            JSONObject copy = JSONUtil.parseObj(params);
            copy.remove("workOrderId");
            copy.remove("batchId");
            copy.remove("sampleSeqs");
            copy.remove("externalTaskId");
            if (!copy.isEmpty()) {
                return copy;
            }
        }
        return new JSONObject();
    }

    private static Object firstSampleConfig(JSONObject params) {
        JSONArray sampleSeqs = params.getJSONArray("sampleSeqs");
        if (sampleSeqs == null || sampleSeqs.isEmpty()) {
            return null;
        }
        JSONObject firstSeq = sampleSeqs.getJSONObject(0);
        if (firstSeq == null) {
            return null;
        }
        JSONArray sampleConfig = firstSeq.getJSONArray("sampleConfig");
        if (sampleConfig == null || sampleConfig.isEmpty()) {
            return null;
        }
        return sampleConfig.get(0);
    }

    private void buildDestinationAndLabware(ExternalTaskDomainRespVO domain, FlowDeviceTaskDO task, JSONObject params) {
        FlowNodeDO nextNode = findNextNode(task);
        if (nextNode != null) {
            applyDestination(domain, nextNode);
        }
        List<String> sampleCodes = extractSampleCodes(params);
        List<ExternalTaskDomainRespVO.Labware> labwareList = new ArrayList<>();
        for (Integer encodedId : materialInstanceExternalApi.listEncodedUsingLabwareIdsBySampleCodes(sampleCodes)) {
            ExternalTaskDomainRespVO.Labware labware = new ExternalTaskDomainRespVO.Labware();
            labware.setId(encodedId);
            labwareList.add(labware);
        }
        domain.setLabwareList(labwareList);
    }

    private FlowNodeDO findNextNode(FlowDeviceTaskDO task) {
        FlowExecutionDO execution = flowExecutionMapper.selectById(task.getExecutionId());
        if (execution == null || execution.getDefinitionId() == null || StrUtil.isBlank(task.getNodeId())) {
            return null;
        }
        List<FlowNodeDO> nodes = flowNodeMapper.selectByDefinitionId(execution.getDefinitionId());
        String currentNodeId = task.getNodeId();
        for (FlowNodeDO node : nodes) {
            if (StrUtil.isBlank(node.getDependsOn())) {
                continue;
            }
            JSONArray deps = JSONUtil.parseArray(node.getDependsOn());
            if (deps.contains(currentNodeId)) {
                return node;
            }
        }
        return null;
    }

    private static void applyDestination(ExternalTaskDomainRespVO domain, FlowNodeDO nextNode) {
        String areaToken = resolveAreaToken(nextNode);
        if (StrUtil.isBlank(areaToken)) {
            return;
        }
        try {
            ExternalAreaEnum areaEnum = ExternalAreaEnum.fromValue(areaToken);
            ExternalTaskDomainRespVO.Destination destination = new ExternalTaskDomainRespVO.Destination();
            destination.setDestination(areaEnum.getCode());
            domain.setDestinationList(List.of(destination));
        } catch (IllegalArgumentException ignored) {
            // 节点 key 非 RA-* 前缀时不填 destination
        }
    }

    private static String resolveAreaToken(FlowNodeDO nextNode) {
        if (nextNode.getNodeId() != null && nextNode.getNodeId().contains("_")) {
            return nextNode.getNodeId().split("_")[0];
        }
        if (StrUtil.isNotBlank(nextNode.getTypeConfig())) {
            JSONObject cfg = JSONUtil.parseObj(nextNode.getTypeConfig());
            String deviceCode = cfg.getStr("deviceCode");
            if (StrUtil.isNotBlank(deviceCode)) {
                return deviceCode;
            }
        }
        return nextNode.getNodeId();
    }

    private static List<String> extractSampleCodes(JSONObject params) {
        List<String> codes = new ArrayList<>();
        JSONArray sampleSeqs = params.getJSONArray("sampleSeqs");
        if (sampleSeqs != null) {
            for (int i = 0; i < sampleSeqs.size(); i++) {
                JSONObject seq = sampleSeqs.getJSONObject(i);
                if (seq == null) {
                    continue;
                }
                String code = seq.getStr("code");
                if (StrUtil.isNotBlank(code)) {
                    codes.add(code.trim());
                }
            }
        }
        if (codes.isEmpty()) {
            String sampleId = params.getStr("sampleId");
            if (StrUtil.isNotBlank(sampleId)) {
                codes.add(sampleId.trim());
            }
        }
        return codes;
    }

    private static JSONObject parseParams(FlowDeviceTaskDO task) {
        if (task == null || StrUtil.isBlank(task.getParamsJson())) {
            return new JSONObject();
        }
        return JSONUtil.parseObj(task.getParamsJson());
    }

}
