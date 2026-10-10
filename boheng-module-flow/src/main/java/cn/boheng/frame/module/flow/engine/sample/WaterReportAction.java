package cn.boheng.frame.module.flow.engine.sample;

import cn.boheng.frame.module.flow.engine.type.builtin.FlowBeanAction;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.doubleOf;
import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.intOf;
import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.map;
import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.parseRaw;
import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.str;

/**
 * 水质检测示例：总报告。主流程收尾节点——汇合理化判定（并行汇合）、
 * 滴定终体积（循环收口）、重金属检测线数量（子流程汇合），产出检测报告。
 */
@Component
public class WaterReportAction implements FlowBeanAction {

    @Override
    public String execute(Map<String, Object> env, Map<String, Object> args) {
        Map<String, Object> node = map(env.get("node"));
        Map<String, Object> input = map(env.get("input"));
        Map<String, Object> merge = map(node.get("merge_check"));
        Map<String, Object> split = map(node.get("split"));
        // 循环与子流程的输出由引擎写入：轮数 / 子执行数
        int iterations = intOf(map(node.get("titration_loop")).get("iterations"), 0);
        int metalLines = intOf(map(node.get("heavy_dispatch")).get("children"), 0);
        double finalVolume = doubleOf(parseRaw(map(node.get("titrate")).get("raw")).get("volume"), 0);

        Map<String, Object> titration = new LinkedHashMap<>();
        titration.put("rounds", iterations);
        titration.put("finalVolume", finalVolume);
        Map<String, Object> metal = new LinkedHashMap<>();
        metal.put("lines", metalLines);
        metal.put("groupA", intOf(map(split.get("output")).get("countA"), 0));
        metal.put("groupB", intOf(map(split.get("output")).get("countB"), 0));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("taskNo", str(input.get("taskNo")));
        report.put("physicalChemical", Map.of(
                "ph", merge.get("ph"),
                "turbidity", merge.get("turbidity"),
                "qualified", merge.get("qualified"),
                "standard", str(merge.get("standard"))));
        report.put("titration", titration);
        report.put("heavyMetal", metal);
        report.put("conclusion", Boolean.TRUE.equals(merge.get("qualified"))
                ? "常规理化指标合格" : "常规理化指标超限，需复检");
        return JSONUtil.toJsonStr(report);
    }

}
