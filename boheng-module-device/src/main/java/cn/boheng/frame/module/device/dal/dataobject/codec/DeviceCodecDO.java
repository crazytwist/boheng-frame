package cn.boheng.frame.module.device.dal.dataobject.codec;

import cn.boheng.frame.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 响应解析规则。一期只使用 JSON 字段映射和正则捕获。
 */
@TableName("device_codec")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCodecDO extends TenantBaseDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String codecCode;

    private String codecName;

    /** JSON 或 REGEX */
    private String parseType;

    /** 输出字段 -> JSON 路径，或 group:N / group:名称 */
    private String fieldMapping;

    private String regexPattern;

    private String sampleRaw;

    /** 0 启用 / 1 停用 */
    private Integer status;

    private String remark;

}
