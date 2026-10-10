package cn.boheng.frame.module.flow.service.definition.model;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 图上的一处问题。nodeId 为空时表示整条流程的问题。
 */
@Data
@AllArgsConstructor
public class FlowIssue {

    /**
     * 出问题的步骤编码。整条流程的问题留空
     */
    private String nodeId;

    /**
     * 给人看的原因
     */
    private String message;

}
