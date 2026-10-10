package cn.boheng.frame.module.flow.enums;

/**
 * 步骤的完成方式。决定执行器返回后调度器怎么收尾。
 */
public enum CompletionMode {

    /**
     * 执行器同步返回结果，当场结算
     */
    SYNC,

    /**
     * 先下发，再等设备回调。执行器返回「等待」，回调由信号路由器结算
     */
    CALLBACK,

    /**
     * 挂起等人工在页面确认
     */
    SIGNAL,

    /**
     * 挂起到指定时刻，由定时任务发信号
     */
    TIMER,

    /**
     * 挂起等子流程终态
     */
    CHILD

}
