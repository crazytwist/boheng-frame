package cn.boheng.frame.module.flow.enums;

/**
 * 步骤类型的角色。角色决定调度器怎么对待这个节点，和「怎么执行」无关。
 */
public enum StepRole {

    /**
     * 动作类：真正干活的一步，如调设备、跑脚本、发通知
     */
    ACTION,

    /**
     * 控制类：只影响图怎么走，不产生业务产出，如 route 路由、loop 循环容器
     */
    CONTROL,

    /**
     * 等待类：把流程挂起等外部信号，如人工确认 manual.task、定时等待 timer.wait
     */
    WAIT,

    /**
     * 子流程类：把另一条流程当作一步来执行，如 subflow 扇出
     */
    SUBFLOW

}
