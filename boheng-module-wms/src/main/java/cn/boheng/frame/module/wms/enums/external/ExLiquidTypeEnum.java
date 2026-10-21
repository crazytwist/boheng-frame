package cn.boheng.frame.module.wms.enums.external;

import lombok.Getter;

/**
 * 清华外部接口 — 液体类型编码（与 qinghua ExLiquidTypeEnum 对齐）
 */
@Getter
public enum ExLiquidTypeEnum {

    LT_None(0, "无"),
    LT_MainSample(1, "主样本"),
    LT_PBS(2, "PBS"),
    LT_Pancreatin(3, "胰酶"),
    LT_Medium(4, "培养基"),
    LT_MediumSuspend(5, "培养基重悬细胞"),
    LT_OPTI_MEM(6, "OPTI-MEM培养基"),
    LT_Virus_Plasmid(7, "病毒质粒"),
    LT_Library_Plasmid(8, "文库质粒"),
    LT_Transfection_Liquid(9, "转染试剂"),
    LT_84(10, "84消毒液"),
    LT_RPMI_1640(11, "RPMI-1640培养基"),
    LT_RPMI_1640_Cold(13, "RPMI-1640培养基（冷）"),
    LT_Lenti_X(12, "Lenti-X concentrator"),
    LT_RPMI_EDTA(14, "RPMI-1640+1%血清+400nM Dasatinib+5mM EDTA（常温）"),
    LT_Hydrogel(15, "水凝胶"),
    LT_MirusTransitX2(16, "Mirus-transitX2或其他"),
    LT_RPMI_EDTA_Cold(17, "RPMI-1640+1%血清+400nM Dasatinib+5mM EDTA（冷）"),
    LT_PRE_HOT_MEDIUM(18, "预热试剂"),
    LT_293T_Virus(19, "293T病毒"),
    LT_Helper_Infection(20, "辅助感染剂"),
    ;

    private final int code;
    private final String description;

    ExLiquidTypeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ExLiquidTypeEnum fromCode(int code) {
        for (ExLiquidTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("No matching liquid type for code: " + code);
    }
}
