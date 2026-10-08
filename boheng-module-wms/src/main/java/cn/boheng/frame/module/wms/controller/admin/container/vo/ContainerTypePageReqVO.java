package cn.boheng.frame.module.wms.controller.admin.container.vo;

import cn.boheng.frame.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

/**
 * 容器类型 分页 Request VO
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 容器类型 分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ContainerTypePageReqVO extends PageParam {

    @Schema(description = "容器类型编码", example = "PLATE_96_WELL")
    private String typeCode;

    @Schema(description = "容器类型名称", example = "96 孔板")
    private String typeName;

    @Schema(description = "物理形态：PLATE 孔板/TUBE 试管/BOTTLE 瓶/RACK 托盘架/VIAL 小瓶/TIP 吸头/CHIP 芯片/FILTER 滤膜/BOX 盒/BAG 袋/OTHER 其他", example = "PLATE")
    private String category;

    @Schema(description = "层级角色：CARRIER 载体/CONTAINER 直接容器/WELL 孔位", example = "CARRIER")
    private String hierarchyRole;

    @Schema(description = "使用类型：REUSABLE 周转复用/DISPOSABLE 一次性", example = "REUSABLE")
    private String usageType;

    @Schema(description = "状态", example = "0")
    private Integer status;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}
