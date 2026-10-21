package cn.boheng.frame.module.flow.enums.external;

import lombok.Getter;

import java.util.Objects;

/**
 * @ClassName ExternalAreaEnum
 * @Author mengke
 * @create 2025/12/3 16:55
 * @Description
 */
@Getter
public enum ExternalAreaEnum {

    RA_Incubation(1, "RA-Incubation", "培养区"),
    RA_TBottle(2, "RA-TBottle", "T瓶处理区"),
    RA_FilterPressure(3, "RA-FilterPressure", "压滤区"),
    RA_LiquidWorkstation(4, "RA-LiquidWorkstation", "移液区");


    private final int code;
    private final String value;
    private final String description;

    ExternalAreaEnum(int code, String value, String description) {
        this.code = code;
        this.value = value;
        this.description = description;
    }


    /**
     * 根据code获取枚举实例
     *
     * @param code 状态编码
     * @return 对应的枚举实例
     */
    public static ExternalAreaEnum fromCode(int code) {
        for (ExternalAreaEnum status : ExternalAreaEnum.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("No matching area for code: " + code);
    }


    /**
     * 根据value获取枚举实例
     *
     * @param value 值编码
     * @return 对应的枚举实例
     */
    public static ExternalAreaEnum fromValue(String value) {
        for (ExternalAreaEnum externalAreaEnum : ExternalAreaEnum.values()) {
            if (Objects.equals(externalAreaEnum.getValue(), value)) {
                return externalAreaEnum;
            }
        }
        throw new IllegalArgumentException("No matching area for value: " + value);
    }


}
