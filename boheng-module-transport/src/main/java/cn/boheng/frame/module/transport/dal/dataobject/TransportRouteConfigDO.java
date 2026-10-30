package cn.boheng.frame.module.transport.dal.dataobject;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/** 静态 Transport 路线配置，不保存运输执行数据。 */
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
@TableName("transport_route_config")
public class TransportRouteConfigDO extends TenantBaseDO {
    /** 主键。 */
    @TableId(type = IdType.AUTO) private Long id;
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
    /** 起点区域编码。 */
    private String sourceAreaCode;
    /** 终点类型。 */
    private String targetType;
    /** 终点站点编码。 */
    private String targetStationCode;
    /** 终点区域编码。 */
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
    /** 路线允许的 AGV 最大装载数量。 */
    private Integer agvLoadCapacity;
    /** 是否需要门禁。 */
    private Boolean doorRequired;
    /** 开门动作能力键。 */
    private String doorOpenActionKey;
    /** 关门动作能力键。 */
    private String doorCloseActionKey;
    /** 绑定流程编码。 */
    private String flowDefinitionKey;
    /** 绑定流程版本。 */
    private Integer flowDefinitionVersion;
    /** 路线优先级。 */
    private Integer priority;
    /** 是否启用。 */
    private Integer enabled;
    /** 备注。 */
    private String remark;
}
