package cn.boheng.frame.module.device.controller.open;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgvTaskCallbackReqDTO {

    public static final String METHOD_START = "start";
    public static final String METHOD_END = "end";

    private String reqCode;             // 平台下发的流水号，设备原样带回 —— 唯一关联键
    private String reqTime;             // yyyy-MM-dd HH:mm:ss
    private String cooX;                // 当前坐标 X
    private String cooY;                // 当前坐标 Y
    private String currentPositionCode; // 当前位置 p02
    private String mapCode;             // 地图编号
    private String mapDataCode;         // 地图数据版本 002069AA015172
    private String method;              // start / end，只有 end 是终态
    private String podCode;             // 货架编号
    private String podDir;              // 货架方向
    private String robotCode;           // 机器人编号
    private String taskCode;            // 厂商任务号，不能用于关联
    private String wbCode;              // 工位编号
}