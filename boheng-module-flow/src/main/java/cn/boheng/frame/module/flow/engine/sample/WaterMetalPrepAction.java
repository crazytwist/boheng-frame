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
import static cn.boheng.frame.module.flow.engine.sample.WaterQualitySupport.str;

/**
 * 水质检测示例：重金属检测备样。运行在重金属检测子流程里，
 * 把扇出分来的本组样本清点造册，交给本线的 ICP-MS 上机检测。
 */
@Component
public class WaterMetalPrepAction implements FlowBeanAction {

    @Override
    public String execute(Map<String, Object> env, Map<String, Object> args) {
        Map<String, Object> input = map(env.get("input"));
        String group = str(input.get("group"));
        List<Object> samples = list(input.get("samples"));
        if (StrUtil.isBlank(group) || samples.isEmpty()) {
            throw new IllegalArgumentException("子流程入参缺少 group / samples（扇出分组的样本清单）");
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("group", group);
        output.put("sampleCount", samples.size());
        output.put("plateId", "MTP-" + group + "-001");
        output.put("samples", samples);
        return JSONUtil.toJsonStr(output);
    }

}
