package cn.boheng.frame.module.flow.engine.sample;

import cn.boheng.frame.module.flow.engine.type.builtin.FlowBeanAction;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.list;
import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.map;

/**
 * 水质检测示例：样本分组。把登记产出的样本清单对半拆成 A / B 两组，
 * 供重金属检测扇出时分发给两条检测线（96 个样本各 48 个）。
 */
@Component
public class WaterSplitAction implements FlowBeanAction {

    @Override
    public String execute(Map<String, Object> env, Map<String, Object> args) {
        Map<String, Object> register = map(map(env.get("node")).get("register"));
        List<Object> samples = list(map(register.get("output")).get("samples"));
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("登记节点没有产出样本清单");
        }
        int half = (samples.size() + 1) / 2;
        List<Object> groupA = samples.subList(0, half);
        List<Object> groupB = samples.subList(half, samples.size());
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("groupA", groupA);
        output.put("groupB", groupB);
        output.put("countA", groupA.size());
        output.put("countB", groupB.size());
        return JSONUtil.toJsonStr(output);
    }

}
