package cn.boheng.frame.module.device.service.callback.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * AGV（海康 RCS 风格）任务回调报文。
 *
 * <p>厂商样例（method=end 的「第二答」）：</p>
 * <pre>
 * {"reqCode":"1541954B96B1112","reqTime":"2019-04-03 10:08:06","cooX":"3000","cooY":"21999",
 *  "currentPositionCode":"p02","mapCode":"AA","mapDataCode":"002069AA015172","method":"end",
 *  "podCode":"100001","podDir":"0","robotCode":"6001","taskCode":"test169E0F39740116Q_ECS",
 *  "wbCode":"p02"}
 * </pre>
 *
 * <p>字段一律用 {@link String} 承接，不做数值/时间类型转换：厂商把坐标、方向、编号
 * 全按字符串发，且空串、缺字段是常态（不同型号机器人回包字段数不一样），
 * 声明成 Integer/Date 只会多出转换异常。要参与计算时在调用处自行转换。</p>
 *
 * <p>关联键只认 {@link #reqCode}：这是平台下发时生成、设备原样带回的流水号，
 * 对应 {@code device_action.correlate_path = $.reqCode}。{@link #taskCode} 是厂商自己的
 * 任务号（如 {@code Task01_xxx}），与平台下发的 data 不是一回事，不能拿来关联。</p>
 *
 * <p>{@link #method} 是阶段标识。回调不一定只在任务结束时来一次，可能先推 {@code start}
 * 或中途进度；只有 {@code end} 才是终态，对应 result_rule 的
 * {@code {"settlePath":"$.method","settleValues":["end"]}} 闸门。</p>
 *
 * @author yinan
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgvTaskCallbackReqDTO {

    /**
     * 任务开始阶段
     */
    public static final String METHOD_START = "start";

    /**
     * 任务结束阶段（终态）
     */
    public static final String METHOD_END = "end";

    /**
     * 请求流水号。平台下发时生成（16 位大写 hex），设备原样带回 —— 唯一的关联键
     */
    private String reqCode;

    /**
     * 请求时间，格式 {@code yyyy-MM-dd HH:mm:ss}
     */
    private String reqTime;

    /**
     * 当前坐标 X
     */
    private String cooX;

    /**
     * 当前坐标 Y
     */
    private String cooY;

    /**
     * 当前位置编号，如 {@code p02}
     */
    private String currentPositionCode;

    /**
     * 地图编号
     */
    private String mapCode;

    /**
     * 地图数据版本号，如 {@code 002069AA015172}
     */
    private String mapDataCode;

    /**
     * 阶段标识：{@link #METHOD_START} / {@link #METHOD_END}。只有 end 才允许结算
     */
    private String method;

    /**
     * 货架（料箱）编号
     */
    private String podCode;

    /**
     * 货架方向
     */
    private String podDir;

    /**
     * 机器人编号
     */
    private String robotCode;

    /**
     * 厂商侧任务号。与平台下发的 data 不同，不能用于关联命令
     */
    private String taskCode;

    /**
     * 工位编号
     */
    private String wbCode;

}
