package cn.boheng.frame.module.flow.service.definition.model;

import lombok.Data;

/**
 * 一条参数从哪里来。保存时收成 JSON，不单独建表。
 */
@Data
public class FlowMapping {

    /**
     * 传给设备动作的参数名
     */
    private String param;

    /**
     * INPUT 启动入参，NODE 上一步输出，LITERAL 固定值
     */
    private String source;

    /**
     * INPUT 或 NODE 时要取的字段名
     */
    private String field;

    /**
     * NODE 时必须是紧挨着的上一步编码
     */
    private String nodeId;

    /**
     * LITERAL 时的固定值
     */
    private String value;

}
