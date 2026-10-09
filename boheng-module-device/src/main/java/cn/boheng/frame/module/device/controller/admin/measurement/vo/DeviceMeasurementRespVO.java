package cn.boheng.frame.module.device.controller.admin.measurement.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
/**
 * 测量记录
 */

@Data
public class DeviceMeasurementRespVO {

    /**
     * 主键，雪花算法
     */
    private Long id;
    /**
     * 关联命令编号
     */
    private Long commandId;
    /**
     * 关联的原始响应编号
     */
    private Long dataRawId;
    /**
     * 设备编号
     */
    private Long deviceId;
    /**
     * 设备编码
     */
    private String deviceCode;
    /**
     * 检测模式，来自解析结果的 measureMode 或 mode
     */
    private String measureMode;
    /**
     * 波长，纳米
     */
    private Integer wavelengthNm;
    /**
     * 方法或脚本名
     */
    private String scriptName;
    /**
     * 这次调用的参数快照
     */
    private String methodParamsJson;
    /**
     * 操作人
     */
    private String operator;
    /**
     * 读数写入时间
     */
    private LocalDateTime readTime;
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    /**
     * 孔位读数
     */
    private List<Well> wells;
    /**
     * 分析结论
     */
    private List<Analysis> analyses;
    /**
     * 一个孔的读数
     */

    @Data
    public static class Well {
        /**
         * 孔位，如 A1
         */
        private String wellPosition;
        /**
         * 波长，纳米
         */
        private Integer wavelengthNm;
        /**
         * 读数值
         */
        private BigDecimal readValue;
        /**
         * 单位
         */
        private String unit;
        /**
         * 解析前的原始文本
         */
        private String rawText;
        /**
         * 质量标记，如 OVER_RANGE
         */
        private String qualityFlag;
    }
    /**
     * 一条分析结论
     */

    @Data
    public static class Analysis {
        /**
         * 结论类型
         */
        private String resultType;
        /**
         * 结论值
         */
        private String resultValue;
        /**
         * 结论来源。解析写入的记为 INTERNAL
         */
        private String sourceSystem;
        /**
         * 解析后的完整对象
         */
        private String summaryJson;
        /**
         * 备注
         */
        private String remark;
    }

}
