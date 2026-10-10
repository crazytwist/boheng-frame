package cn.boheng.frame.module.flow.engine.sample;

import cn.boheng.frame.module.flow.engine.dispatch.FlowDispatchAction;
import cn.boheng.frame.module.flow.engine.type.config.DispatchPlanEntry;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.list;
import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.map;

/**
 * 水质检测示例：重金属检测扇出分发。读取分样节点的 A / B 两组样本，
 * 产出「A 组走 1 号检测线、B 组走 2 号检测线」的拆分计划——
 * 每组一条计划，引擎按计划幂等创建子执行，扇出点即汇合点。
 */
@Component
public class WaterGroupDispatchAction implements FlowDispatchAction {

    @Override
    public List<DispatchPlanEntry> dispatch(Map<String, Object> env) {
        Map<String, Object> split = map(map(env.get("node")).get("split"));
        Map<String, Object> output = map(split.get("output"));
        List<Object> groupA = list(output.get("groupA"));
        List<Object> groupB = list(output.get("groupB"));
        if (groupA.isEmpty() && groupB.isEmpty()) {
            throw new IllegalArgumentException("分样节点没有产出可分发的样本组");
        }
        List<DispatchPlanEntry> plan = new ArrayList<>();
        if (!groupA.isEmpty()) {
            plan.add(entry("wq_metal_line_a", "A", groupA));
        }
        if (!groupB.isEmpty()) {
            plan.add(entry("wq_metal_line_b", "B", groupB));
        }
        return plan;
    }

    /**
     * 一组一条计划：子流程编码 + 本组入参
     */
    private DispatchPlanEntry entry(String pipelineKey, String group, List<Object> samples) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("group", group);
        input.put("samples", samples);
        DispatchPlanEntry entry = new DispatchPlanEntry();
        entry.setPipelineKey(pipelineKey);
        entry.setInputJson(JSONUtil.toJsonStr(input));
        return entry;
    }

}
