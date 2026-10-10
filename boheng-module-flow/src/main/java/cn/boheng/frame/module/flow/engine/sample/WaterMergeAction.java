package cn.boheng.frame.module.flow.engine.sample;

import cn.boheng.frame.module.flow.engine.type.builtin.FlowBeanAction;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.doubleOf;
import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.map;
import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.parseRaw;

/**
 * 水质检测示例：理化指标汇总。pH 与浊度两条并行检测线完成后汇合，
 * 在这里按《生活饮用水卫生标准》的常规限值做一次合格判定。
 */
@Component
public class WaterMergeAction implements FlowBeanAction {

    /**
     * pH 合格下限
     */
    private static final double PH_MIN = 6.0;

    /**
     * pH 合格上限
     */
    private static final double PH_MAX = 9.0;

    /**
     * 浊度限值（NTU）
     */
    private static final double TURBIDITY_MAX = 5.0;

    @Override
    public String execute(Map<String, Object> env, Map<String, Object> args) {
        Map<String, Object> node = map(env.get("node"));
        Map<String, Object> phRaw = parseRaw(map(node.get("ph_test")).get("raw"));
        Map<String, Object> tbRaw = parseRaw(map(node.get("turbidity_test")).get("raw"));
        double ph = doubleOf(phRaw.get("ph"), -1);
        double turbidity = doubleOf(tbRaw.get("turbidity"), -1);
        boolean qualified = ph >= PH_MIN && ph <= PH_MAX && turbidity < TURBIDITY_MAX;
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("ph", ph);
        output.put("turbidity", turbidity);
        output.put("qualified", qualified);
        output.put("standard", "GB 5749-2022");
        return JSONUtil.toJsonStr(output);
    }

}
