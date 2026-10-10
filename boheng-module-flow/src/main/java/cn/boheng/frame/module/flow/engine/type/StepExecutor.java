package cn.boheng.frame.module.flow.engine.type;

/**
 * 一种步骤类型的执行器。只负责「这一步怎么执行」，返回 StepOutcome 即止；
 * 推进图、写运行表、重试全部归调度器。
 *
 * <p>普通 ACTION / WAIT 类型的实现不得注入调度器或运行表 Mapper（架构约束）。
 * loop / subflow 等容器类型由调度器直接驱动，不配执行器。
 */
public interface StepExecutor {

    /**
     * 对应的类型 key。与 Descriptor 成对注册。
     */
    String type();

    /**
     * 执行一个节点。SYNC 类型当场返回结果；CALLBACK/SIGNAL/TIMER/CHILD
     * 类型完成下发后返回 WAITING，后续由信号路由器结算。
     */
    StepOutcome execute(StepExecutionContext ctx);

}
