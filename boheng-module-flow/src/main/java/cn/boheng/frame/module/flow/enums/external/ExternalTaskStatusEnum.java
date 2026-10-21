package cn.boheng.frame.module.flow.enums.external;

import lombok.Getter;

/**
 * @ClassName ExternalTaskStatusEnum
 * @Author mengke
 * @create 2025/12/3 14:46
 * @Description 外部任务状态枚举
 */
@Getter
public enum ExternalTaskStatusEnum {

    SS_Idle(0, "空闲"),
    SS_Running(1, "运行中"),
    SS_Finished(2, "已完成"),
    SS_Error(3, "错误"),
    SS_Pause(4, "暂停"),
    SS_Wait(5, "等待"),
    SS_Occupy(6, "占用"),
    ;

    private final int code;
    private final String description;

    ExternalTaskStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举实例
     *
     * @param code 状态编码
     * @return 对应的枚举实例
     */
    public static ExternalTaskStatusEnum fromCode(int code) {
        for (ExternalTaskStatusEnum status : ExternalTaskStatusEnum.values()) {
            if (status.getCode() == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("No matching status for code: " + code);
    }

    /**
     * 根据枚举名称获取code
     *
     * @param name 枚举名称
     * @return 对应的code
     */
    public static int getCodeByName(String name) {
        try {
            return ExternalTaskStatusEnum.valueOf(name).getCode();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("No matching status for name: " + name);
        }
    }

}
