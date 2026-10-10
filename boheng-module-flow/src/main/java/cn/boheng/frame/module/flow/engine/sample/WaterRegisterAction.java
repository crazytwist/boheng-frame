package cn.boheng.frame.module.flow.engine.sample;

import cn.boheng.frame.module.flow.engine.type.builtin.FlowBeanAction;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.intOf;
import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.map;
import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.str;

/**
 * 水质检测示例：样品登记。按启动入参的任务号和样本数生成样本清单，
 * 后续分样、设备检测、报告都围绕这份清单流转。
 */
@Component
public class WaterRegisterAction implements FlowBeanAction {

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
        List<String> samples = new ArrayList<>();
        for (int i = 1; i <= sampleCount; i++) {
            samples.add(String.format("%s-%03d", taskNo, i));
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("taskNo", taskNo);
        output.put("sampleCount", sampleCount);
        output.put("samples", samples);
        return JSONUtil.toJsonStr(output);
    }

}
