package cn.boheng.frame.module.flow.engine.sample;

import cn.boheng.frame.module.flow.engine.type.builtin.FlowBeanAction;
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

        List<String> samples = new ArrayList<>(sampleCount);
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
            samples.add(sampleApi.registerSample(req));
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("taskNo", taskNo);
        output.put("sampleCount", sampleCount);
        output.put("sampleType", sampleType);
        output.put("samples", samples);
        return JSONUtil.toJsonStr(output);
    }

}
