package cn.boheng.frame.module.flow.engine.type;

import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 步骤类型注册表。启动时收集所有 Descriptor / Executor，
 * 校验 key 不重复、Descriptor 与 Executor 成对、配置类型可反序列化。
 * 定义层发布校验和运行层分发都从这里取类型。
 */
@Component
public class StepTypeRegistry {

    /**
     * 全部类型说明书，按 key 索引
     */
    @Resource
    private List<StepTypeDescriptor> descriptors;

    /**
     * 全部执行器，按 key 索引
     */
    @Resource
    private List<StepExecutor> executors;

    /**
     * key -> 说明书
     */
    private final Map<String, StepTypeDescriptor> descriptorMap = new HashMap<>();

    /**
     * key -> 执行器
     */
    private final Map<String, StepExecutor> executorMap = new HashMap<>();

    /**
     * 启动时建索引并做自检。任何一条不满足都直接让应用起不来：
     * 类型系统是发布校验的地基，带病启动只会把错误推迟到运行期。
     */
    @PostConstruct
    public void init() {
        for (StepTypeDescriptor descriptor : descriptors) {
            if (descriptor.type() == null || descriptor.type().isBlank()) {
                throw new IllegalStateException("存在 type 为空的 StepTypeDescriptor: " + descriptor.getClass().getName());
            }
            StepTypeDescriptor previous = descriptorMap.put(descriptor.type(), descriptor);
            if (previous != null) {
                throw new IllegalStateException("步骤类型重复注册: " + descriptor.type());
            }
        }
        for (StepExecutor executor : executors) {
            StepExecutor previous = executorMap.put(executor.type(), executor);
            if (previous != null) {
                throw new IllegalStateException("步骤执行器重复注册: " + executor.type());
            }
            if (!descriptorMap.containsKey(executor.type())) {
                throw new IllegalStateException("执行器没有对应的 Descriptor: " + executor.type());
            }
        }
        // Descriptor 允许没有执行器：loop / subflow 等容器类型由调度器直接驱动
    }

    /**
     * 类型是否已注册
     */
    public boolean isRegistered(String type) {
        return type != null && descriptorMap.containsKey(type);
    }

    /**
     * 取说明书。不存在返回 null，由调用方决定怎么报错
     */
    public StepTypeDescriptor getDescriptor(String type) {
        return type == null ? null : descriptorMap.get(type);
    }

    /**
     * 取执行器
     */
    public StepExecutor getExecutor(String type) {
        return type == null ? null : executorMap.get(type);
    }

    /**
     * 把 type_config JSON 反序列化成强类型配置。
     * 配置为空且类型要求必填时返回 null，由 Descriptor.validate 报具体问题。
     */
    public Object parseConfig(String type, String configJson) {
        StepTypeDescriptor descriptor = getDescriptor(type);
        if (descriptor == null) {
            return null;
        }
        if (StrUtil.isBlank(configJson)) {
            return null;
        }
        return JSONUtil.toBean(configJson, descriptor.configType());
    }

    /**
     * 内置类型 key 的便捷判断
     */
    public static boolean isControlType(String type) {
        return FlowConstants.STEP_ROUTE.equals(type)
                || FlowConstants.STEP_LOOP.equals(type)
                || FlowConstants.STEP_SUBFLOW.equals(type)
                || FlowConstants.STEP_TIMER_WAIT.equals(type);
    }

}
