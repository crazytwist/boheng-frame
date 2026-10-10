package cn.boheng.frame.module.flow.engine.type;

import cn.boheng.frame.module.flow.enums.CompletionMode;
import cn.boheng.frame.module.flow.enums.StepRole;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;

import java.util.List;

/**
 * 一种步骤类型的说明书。定义层用它做发布校验，运行层用它决定怎么收尾。
 * 新增一种能力 = 写一个 Descriptor + 一个 Executor，注册进 Spring 容器即可，不改 Flow 核心。
 */
public interface StepTypeDescriptor {

    /**
     * 类型唯一 key，如 device.command。注册表按它去重。
     */
    String type();

    /**
     * 角色。决定调度器怎么对待这个节点。
     */
    StepRole role();

    /**
     * 完成方式。决定执行器返回后调度器怎么结算。
     */
    CompletionMode completionMode();

    /**
     * type_config 反序列化的目标类型。配置 POJO 放在 engine/type/config 下。
     */
    Class<?> configType();

    /**
     * 失败后是否允许重试。物理动作可以重发，人工确认重试没有意义。
     */
    default boolean retryable() {
        return true;
    }

    /**
     * 发布期校验类型专属配置。发现问题往 issues 里追加，nodeId 由调用方带入。
     */
    void validate(Object config, String nodeId, List<FlowIssue> issues);

}
