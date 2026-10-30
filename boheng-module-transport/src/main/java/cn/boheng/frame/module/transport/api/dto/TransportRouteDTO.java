package cn.boheng.frame.module.transport.api.dto;

import lombok.Data;

/** 静态运输路线快照。 */
@Data
public class TransportRouteDTO {
    /** 路线编码。 */
    private String routeCode;
    /** 路线名称。 */
    private String routeName;
    /** 路线类型。 */
    private String routeType;
    /** 起点类型。 */
    private String sourceType;
    /** 起点站点编码。 */
    private String sourceStationCode;
    /** 起点区域编码，供 WMS 查询中转位。 */
    private String sourceAreaCode;
    /** 终点类型。 */
    private String targetType;
    /** 终点站点编码。 */
    private String targetStationCode;
    /** 终点区域编码，供 WMS 查询中转位。 */
    private String targetAreaCode;
    /** AGV 移动动作能力键。 */
    private String moveActionKey;
    /** AGV 取料动作能力键。 */
    private String grabActionKey;
    /** AGV 放料动作能力键。 */
    private String placeActionKey;
    /** 仓储出料动作能力键。 */
    private String warehouseLoadActionKey;
    /** 仓储入料动作能力键。 */
    private String warehouseUnloadActionKey;
    /** AGV 任务类型。 */
    private String agvTaskType;
    /** AGV 托板类型。 */
    private String plateType;
    /** 该路线允许的 AGV 最大装载数量。 */
    private Integer agvLoadCapacity;
    /** 是否需要门禁。 */
    private Boolean doorRequired;
    /** 开门动作能力键。 */
    private String doorOpenActionKey;
    /** 关门动作能力键。 */
    private String doorCloseActionKey;
    /** 绑定的流程编码。 */
    private String flowDefinitionKey;
    /** 绑定的流程版本。 */
    private Integer flowDefinitionVersion;
}
