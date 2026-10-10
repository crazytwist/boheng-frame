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

/**
 * 水质检测示例：滴定轮次检查。loop 循环体每跑一轮在这里记数并判定
 * 是否达到目标轮次——产出的 needMore 就是循环 while 表达式的判定变量，
 * 演示「循环条件由循环体内的节点驱动」。
 */
@Component
public class WaterTitrationCheckAction implements FlowBeanAction {

    /**
     * 默认平行测定轮次（GB/T 601 化学试剂滴定的常见平行样数）
     */
    private static final int DEFAULT_TARGET_ROUNDS = 2;

    @Override
    public String execute(Map<String, Object> env, Map<String, Object> args) {
        Map<String, Object> node = map(env.get("node"));
        // 上一轮的记数（第 1 轮时还没有）
        Map<String, Object> previous = map(node.get("titrate_check"));
        int round = intOf(previous.get("round"), 0);
        int targetRounds = intOf(args.get("targetRounds"), DEFAULT_TARGET_ROUNDS);
        // 本轮滴定体积（设备报文经 output_mapping 映出 raw）
        double volume = doubleOf(parseRaw(map(node.get("titrate")).get("raw")).get("volume"), 0);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("round", round + 1);
        output.put("targetRounds", targetRounds);
        output.put("volume", volume);
        output.put("needMore", round + 1 < targetRounds);
        return JSONUtil.toJsonStr(output);
    }

}
