package cn.boheng.frame.module.wms.enums.external;

import lombok.Getter;

/**
 * 清华外部接口 — 器具状态编码（与 qinghua ExLabwareStatusEnum 对齐）
 */
@Getter
public enum ExLabwareStatusEnum {

    LS_New(0, "新建"),
    LS_Using(1, "使用中"),
    LS_Discard(2, "废弃"),
    LS_Harverst(3, "收获"),
    LS_Used(4, "使用过了"),
    ;

    private final int code;
    private final String description;

    ExLabwareStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ExLabwareStatusEnum fromCode(int code) {
        for (ExLabwareStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("No matching labware status for code: " + code);
    }
}
