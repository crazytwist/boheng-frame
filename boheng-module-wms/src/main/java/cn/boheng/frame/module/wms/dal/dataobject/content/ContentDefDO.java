package cn.boheng.frame.module.wms.dal.dataobject.content;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 内容物定义 DO
 *
 * 对应表 wms_content_def，继承 TenantBaseDO，由租户拦截器自动隔离
 *
 * @author yinan
 */
@TableName("wms_content_def")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentDefDO extends TenantBaseDO {

    /**
     * 主键（雪花算法，应用侧生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 内容物编码
     */
    private String contentCode;

    /**
     * 内容物名称
     */
    private String contentName;

    /**
     * 图片地址（可空，前端展示默认图）
     */
    private String imageUrl;

    /**
     * 内容物类型：REAGENT 试剂/STANDARD 标准品/BUFFER 缓冲液/SAMPLE 样本/WASTE 废液/MEDIA 培养基/SOLVENT 溶剂/OTHER 其他
     */
    private String contentType;

    /**
     * 计量单位：UL/ML/MG/G/COUNT
     */
    private String unit;

    /**
     * 供应商名称
     */
    private String supplier;

    /**
     * 供应商货号
     */
    private String catalogNo;

    /**
     * CAS 号
     */
    private String casNo;

    /**
     * 标准浓度描述
     */
    private String concentration;

    /**
     * 存储条件：RT 常温/C2_8 2-8℃/F20 -20℃/Ultra80 -80℃/FROZTHAW 冻融
     */
    private String storageCond;

    /**
     * 保质期（天，为空不限）
     */
    private Integer shelfLifeDays;

    /**
     * 开封后有效期（天，为空不限）
     */
    private Integer openLifeDays;

    /**
     * 危险品等级：NONE/LOW/MEDIUM/HIGH/FLAMMABLE/TOXIC
     */
    private String hazardLevel;

    /**
     * 是否 FEFO 出库
     */
    private Boolean fefo;

    /**
     * 其他规格参数（JSON）
     */
    private String specJson;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 备注
     */
    private String description;

}
