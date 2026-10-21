package cn.boheng.frame.module.wms.enums.external;

import lombok.Getter;

/**
 * 清华外部接口 — 器具/物料类型编码（与 qinghua ExLabwareTypeEnum 对齐）
 */
@Getter
public enum ExLabwareTypeEnum {

    LabwareType_None(0, "无"),
    LabwareType_T75Tube(1, "T75培养瓶"),
    LabwareType_50CentrifugeTube(2, "50ml离心管"),
    LabwareType_10mlTip(3, "10ml吸头"),
    LabwareType_1mlTip(4, "1ml吸头"),
    LabwareType_96_Board(5, "96板"),
    LabwareType_6_Board(6, "6板"),
    LabwareType_2CentrifugeTube(7, "2ml离心管"),
    LabwareType_50CentrifugeTube_Board(8, "50ml离心管载架"),
    LabwareType_50Syringe(9, "50ml注射器"),
    LabwareType_2LTube(10, "2L瓶"),
    LabwareType_Filter50CentrifugeTube(11, "滤网离心管"),
    ;

    private final int code;
    private final String description;

    ExLabwareTypeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ExLabwareTypeEnum fromCode(int code) {
        for (ExLabwareTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("No matching labware type for code: " + code);
    }
}
