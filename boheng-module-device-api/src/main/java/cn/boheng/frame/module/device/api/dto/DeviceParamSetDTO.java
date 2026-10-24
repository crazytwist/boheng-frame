package cn.boheng.frame.module.device.api.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 设备参数集摘要 DTO。
 *
 * <p>供流程等消费方读取已验证的默认入参，不暴露设备域的持久化对象。
 *
 * @author yinan
 */
@Data
public class DeviceParamSetDTO implements Serializable {

    /** 参数集主键 */
    private Long id;

    /** 参数集编码 */
    private String paramSetCode;

    /** 参数集名称 */
    private String paramSetName;

    /** 适用的设备类型编码 */
    private String deviceTypeCode;

    /** 适用的设备动作编码 */
    private String actionCode;

    /** 可作为设备调用默认值的 JSON 对象 */
    private String paramsJson;
}
