package cn.boheng.frame.module.device.controller.admin.measurement.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class DeviceMeasurementRespVO {

    private Long id;
    private Long commandId;
    private Long dataRawId;
    private Long deviceId;
    private String deviceCode;
    private String measureMode;
    private Integer wavelengthNm;
    private String scriptName;
    private String methodParamsJson;
    private String operator;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
    private List<Well> wells;
    private List<Analysis> analyses;

    @Data
    public static class Well {
        private String wellPosition;
        private Integer wavelengthNm;
        private BigDecimal readValue;
        private String unit;
        private String rawText;
        private String qualityFlag;
    }

    @Data
    public static class Analysis {
        private String resultType;
        private String resultValue;
        private String sourceSystem;
        private String summaryJson;
        private String remark;
    }

}
