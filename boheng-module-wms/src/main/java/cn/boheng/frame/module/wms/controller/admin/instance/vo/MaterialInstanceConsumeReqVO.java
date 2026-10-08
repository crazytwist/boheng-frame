package cn.boheng.frame.module.wms.controller.admin.instance.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料实例 消耗 Request VO
 *
 * <p><b>精确消耗</b>：items 里每一项都指向**被消耗的那个实例本身**（可以是根实例，也可以是子实例），
 * 后端只递减该实例自身的数量 / 体积，不会替你去挑它的子实例。</p>
 *
 * <p>两种计量方式，三选一：</p>
 * <ul>
 *   <li><b>离散计数 qty</b>：24 根离心管用掉 4 根 → qty = 4，实例 current_count 24 → 20</li>
 *   <li><b>连续体积 volUl</b>：500mL 缓冲液用掉 200mL → volUl = 200，current_vol_ul 500 → 300</li>
 *   <li><b>两者都不传</b>：整件消耗 —— 把这个实例一次用完（剩余量归零，状态转 USED）。
 *       这是最常见的「这根管子用掉了」场景。</li>
 * </ul>
 *
 * <p>状态联动：消耗后剩余量 &gt; 0 → IN_USE；剩余量归零 → USED。
 * 也可用 item 上的 afterStatus 显式覆盖。</p>
 *
 * <p>若调用方只知道「要从这个容器拿 4 根」而不知道具体是哪几个子实例，
 * 请改用 {@code /wms/material-instance/consume-by-container}。</p>
 *
 * @author yinan
 */
@Schema(description = "管理后台 - 物料实例 消耗 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MaterialInstanceConsumeReqVO extends MovementTraceBaseReqVO {

    @Schema(description = "消耗明细列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "消耗明细不能为空")
    @Valid
    private List<Item> items;

    @Schema(description = "管理后台 - 物料实例 消耗明细")
    @Data
    public static class Item {

        @Schema(description = "被消耗的实例编号（叶实例传自身；整个实例被消耗完也传自身编号）",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
        @NotNull(message = "实例编号不能为空")
        private Long instanceId;

        @Schema(description = "离散消耗数量（个）；与 volUl 二选一，优先 volUl", example = "4")
        @Positive(message = "消耗数量必须大于 0")
        private Integer qty;

        @Schema(description = "连续消耗体积（μL）；与 qty 二选一，优先本字段", example = "200.00")
        @Positive(message = "消耗体积必须大于 0")
        private BigDecimal volUl;

        @Schema(description = "消耗后的实例状态（可空 = 按剩余量自动推导：>0 → IN_USE，=0 → USED）", example = "USED")
        private String afterStatus;

        @Schema(description = "明细级备注", example = "取 4 根离心管")
        private String remark;

    }

}
