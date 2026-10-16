package cn.boheng.frame.module.wms.dal.dataobject.sample;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 样本主表 DO
 *
 * <p>样本是「业务对象」，独立于实物（wms_material_instance）。样本可提前登记，
 * 实例化时把 sample_id 挂到 material_instance 上。三层模型：</p>
 * <pre>
 *   wms_content_def        物质定义（样本「是什么物质」）
 *   wms_sample_info        样本主表（谱系/状态/实验参数/流程关联）—— 本表
 *   wms_material_instance  实物实例（sample_id 关联样本）
 * </pre>
 *
 * 对应表 wms_sample_info，继承 TenantBaseDO，由租户拦截器自动隔离
 *
 * @author yinan
 */
@TableName("wms_sample_info")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SampleInfoDO extends TenantBaseDO {

    /**
     * 主键（雪花算法，应用侧生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 样本业务编号（条码/二维码，租户内唯一）
     */
    private String sampleId;

    /**
     * 父样本编号（NULL=原始样本）
     */
    private String parentSampleId;

    /**
     * 根样本编号（原始样本=自身，查整棵谱系树）
     */
    private String rootSampleId;

    /**
     * 谱系代数（原始=0，每拆分/合并一次 +1）
     */
    private Integer generation;

    /**
     * 衍生类型: ORIGINAL 原始/SPLIT 拆分/MERGE 合并/ALIQUOT 分装/ADD_REAGENT 加试剂
     */
    private String deriveType;

    /**
     * 样本类型（字典 wms_sample_type）: WATER 水样/BLOOD 血样/SOIL 土样/...
     */
    private String sampleType;

    /**
     * 样本名称/描述
     */
    private String sampleName;

    /**
     * 内容物定义编号（样本是什么物质，关联 wms_content_def）
     */
    private Long contentDefId;

    /**
     * 内容物编码（冗余，编码寻址）
     */
    private String contentDefCode;

    /**
     * 期望容器类型编码（登记时可指定，关联 wms_container_type）
     */
    private String containerTypeCode;

    /**
     * 初始体积（μL，登记承诺量，不再变更；当前量在实例 current_vol_ul）
     */
    private BigDecimal initialVolUl;

    /**
     * 实验参数（JSON，流程步骤执行时读，如 {"targetPh":7.2,"volumeUl":500}）
     */
    private String experimentParams;

    /**
     * 扩展属性（JSON，不同类型样本不同属性，如 {"ph":7.2,"color":"透明"}）
     */
    private String extData;

    /**
     * 样本状态: REGISTERED/LOADED/IN_PROCESS/SPLIT/COMPLETED/ARCHIVED/REJECTED/LOST
     */
    private String status;

    /**
     * 当前流程执行编号（样本在哪个流程）
     */
    private String currentExecutionId;

    /**
     * 当前所在步骤节点编号
     */
    private String currentNodeId;

    /**
     * 样本批次号（同一批送检样本共享；区别于实例的入库批次）
     */
    private String batchNo;

    /**
     * 检测单/委托单号
     */
    private String orderNo;

    /**
     * 优先级: 0 普通/1 加急/2 特急
     */
    private Integer priority;

    /**
     * 来源: MANUAL 手动/IMPORT 导入/LIS 系统对接/DEVICE 设备采集
     */
    private String source;

    /**
     * 外部系统 ID（LIS 单号等）
     */
    private String externalId;

    /**
     * 采样时间
     */
    private LocalDateTime collectedAt;

    /**
     * 收样时间（到达实验室）
     */
    private LocalDateTime receivedAt;

    /**
     * 样本有效期
     */
    private LocalDateTime expireTime;

    /**
     * 备注
     */
    private String description;

}
