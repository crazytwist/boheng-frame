package cn.boheng.frame.module.flow.engine.sample;

import cn.boheng.frame.module.flow.engine.type.builtin.FlowBeanAction;
import cn.boheng.frame.module.flow.enums.FlowTraceKeys;
import cn.boheng.frame.module.wms.api.SampleApi;
import cn.boheng.frame.module.wms.api.dto.SampleRegisterReqDTO;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.intOf;
import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.map;
import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.str;

/**
 * 水质检测示例：样品登记。按启动入参的任务号和样本数，把样本提前登记进 WMS
 * （经 SampleApi 契约，落到 wms_sample_info），产出真实样本编号清单；
 * 后续分样、设备检测、报告都围绕这份清单流转。
 *
 * <p>样本是「业务对象」，先于实物（material_instance）建档；实例化时再把
 * sample_id 挂到实物上。本节点只做登记，不产生实物。</p>
 *
 * <p>节点重试走 {@link SampleApi#registerSamples} 批量同态幂等：同 taskNo 重复登记
 * 不会因 uk_sample_id 失败。Flow 侧幂等键按 {@link FlowTraceKeys} 生成，写入产出便于追溯。</p>
 */
@Component
public class WaterRegisterAction implements FlowBeanAction {

    @Resource
    private SampleApi sampleApi;

    @Override
    public String execute(Map<String, Object> env, Map<String, Object> args) {
        Map<String, Object> input = map(env.get("input"));
        String taskNo = str(input.get("taskNo"));
        int sampleCount = intOf(input.get("sampleCount"), 0);
        if (StrUtil.isBlank(taskNo)) {
            throw new IllegalArgumentException("启动入参缺少 taskNo（检测任务号）");
        }
        if (sampleCount < 1) {
            throw new IllegalArgumentException("启动入参 sampleCount 至少为 1");
        }
        // 可选的样本属性：默认水样，类型/容器/物质/批次可由启动入参覆盖
        String sampleType = StrUtil.blankToDefault(str(input.get("sampleType")), "WATER");
        String contentDefCode = str(input.get("contentDefCode"));
        String containerTypeCode = str(input.get("containerTypeCode"));
        String batchNo = str(input.get("batchNo"));

        Map<String, Object> sys = map(env.get("sys"));
        Map<String, Object> safeArgs = args == null ? Map.of() : args;
        String idempotentKey = buildRegisterIdempotentKey(sys, safeArgs);

        List<SampleRegisterReqDTO> reqs = new ArrayList<>(sampleCount);
        for (int i = 1; i <= sampleCount; i++) {
            String sampleId = String.format("%s-%03d", taskNo, i);
            SampleRegisterReqDTO req = new SampleRegisterReqDTO();
            req.setSampleId(sampleId);
            req.setSampleName("水质样本 " + sampleId);
            req.setSampleType(sampleType);
            req.setContentDefCode(contentDefCode);
            req.setContainerTypeCode(containerTypeCode);
            req.setBatchNo(StrUtil.blankToDefault(batchNo, taskNo));
            req.setOrderNo(taskNo);
            req.setSource("IMPORT");
            reqs.add(req);
        }
        List<String> samples = sampleApi.registerSamples(reqs);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("taskNo", taskNo);
        output.put("sampleCount", sampleCount);
        output.put("sampleType", sampleType);
        output.put("samples", samples);
        if (StrUtil.isNotBlank(idempotentKey)) {
            output.put("idempotentKey", idempotentKey);
        }
        return JSONUtil.toJsonStr(output);
    }

    /**
     * 按 Phase A 约定生成本节点登记操作的幂等键；缺执行上下文时返回 null（样本侧仍靠 sampleId 同态幂等）。
     */
    private String buildRegisterIdempotentKey(Map<String, Object> sys, Map<String, Object> args) {
        Long executionId = toLong(sys.get("executionId"));
        if (executionId == null) {
            return null;
        }
        String nodeId = StrUtil.blankToDefault(str(args.get("nodeId")), beanName());
        int iterationNo = intOf(sys.get("iterationNo"), 0);
        int attempt = intOf(sys.get("attempt"), 1);
        return FlowTraceKeys.idempotentKey(executionId, nodeId, iterationNo, attempt,
                FlowTraceKeys.OP_REGISTER_SAMPLE);
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && StrUtil.isNotBlank(s)) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

}
