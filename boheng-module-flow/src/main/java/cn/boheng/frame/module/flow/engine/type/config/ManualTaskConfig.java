package cn.boheng.frame.module.flow.engine.type.config;

import lombok.Data;

/**
 * manual.task 的类型配置。执行时节点挂起，等人在运行页确认。
 */
@Data
public class ManualTaskConfig {

    /**
     * 给确认人看的说明
     */
    private String instruction;

    /**
     * 确认时是否必须填写备注
     */
    private Boolean requireComment;

}
