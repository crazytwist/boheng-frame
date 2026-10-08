package cn.boheng.frame.module.wms.dal.dataobject.movement;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料流水 DO
 *
 * 对应表 wms_material_movement，继承 TenantBaseDO，由租户拦截器自动隔离。
 *
 * ⚠️ 本表**只增不改**：Service 层只暴露 insert / select，不提供 update / delete。
 * 操作错了写一条 ADJUST 反向冲正流水，不删除原记录。
 *
 * 设计前提：当前模型是「位置即实例」，上架/下架/转移本质是
 * {@code wms_material_instance.root_slot_id} 的三次变更，故流水主体是**实例**。
 *
 * @author yinan
 */
@TableName("wms_material_movement")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialMovementDO extends TenantBaseDO {

    /**
     * 主键（雪花算法，应用侧生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 流水类型：CREATE 建账/PUT_IN 上架/TAKE_OUT 下架/MOVE 转移/CONSUME 消耗/
     * STATUS_CHANGE 状态变更/RESERVE 预留/RELEASE 释放预留/ADJUST 冲正
     */
    private String movementType;

    /**
     * 业务来源：MANUAL 手工/TASK 调度任务/DEVICE 设备/IMPORT 导入
     */
    private String bizSource;

    /**
     * 操作批次号（一次批量操作的多条明细共享）
     */
    private String operationId;

    // ========== 主体：实例 ==========

    /**
     * 实例编号（流水主体，可以是根实例也可以是子实例）
     */
    private Long instanceId;

    /**
     * 实例编码（冗余）
     */
    private String instanceCode;

    /**
     * 根实例编号（冗余，定位本条流水属于哪个顶层容器）
     */
    private Long rootInstanceId;

    /**
     * 容器类型编码快照
     */
    private String containerTypeCode;

    /**
     * 内容物编码快照
     */
    private String contentDefCode;

    /**
     * 内容物类型快照
     */
    private String contentType;

    // ========== 位置：from → to ==========

    /**
     * 源槽位编号（空 = 上架/建账，操作前不在架上）
     */
    private Long fromSlotId;

    /**
     * 源槽位编码（冗余）
     */
    private String fromSlotCode;

    /**
     * 源区域编码（冗余）
     */
    private String fromZoneCode;

    /**
     * 目标槽位编号（空 = 下架/消耗，操作后不在架上）
     */
    private Long toSlotId;

    /**
     * 目标槽位编码（冗余）
     */
    private String toSlotCode;

    /**
     * 目标区域编码（冗余）
     */
    private String toZoneCode;

    // ========== 状态 ==========

    /**
     * 变更前实例状态
     */
    private String beforeStatus;

    /**
     * 变更后实例状态
     */
    private String afterStatus;

    // ========== 数量 / 体积 ==========

    /**
     * 变更前数量（个）
     */
    private Integer beforeQty;

    /**
     * 变更量（个，正为增负为减）
     */
    private Integer changeQty;

    /**
     * 变更后数量（个）
     */
    private Integer afterQty;

    /**
     * 变更前体积（μL）
     */
    private BigDecimal beforeVolUl;

    /**
     * 变更量（μL，负为消耗）
     */
    private BigDecimal changeVolUl;

    /**
     * 变更后体积（μL）
     */
    private BigDecimal afterVolUl;

    // ========== 追溯 ==========

    /**
     * 关联业务类型：TASK 调度任务/ORDER 单据/API 外部调用/CHECK 盘点
     */
    private String refType;

    /**
     * 关联业务编号（跨模块只认编码，不建外键）
     */
    private String refId;

    /**
     * 关联业务单号
     */
    private String refNo;

    /**
     * 幂等键（外部模块调用防重复过账；为空不参与唯一约束）
     */
    private String idempotentKey;

    // ========== 操作者 ==========

    /**
     * 操作人（手工 = 登录用户；调度/设备 = system 或设备编码）
     */
    private String operator;

    /**
     * 操作者类型：USER 人工/DEVICE 设备/AUTO 自动
     */
    private String operatorType;

    /**
     * 业务操作时间（≠ 入库时间，支持补录历史）
     */
    private LocalDateTime operateTime;

    /**
     * 备注 / 操作原因（下架不强制填写）
     */
    private String remark;

}
