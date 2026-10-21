package cn.boheng.frame.module.flow.controller.app.task.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 按区域（工位）查询待执行设备任务。清华 {@code listTaskByArea} 迁移：请求里的 area 对应 {@code flow_device_task.device_code}。
 */
@Schema(description = "机器端 - 按区域查询待执行设备任务 Request VO")
@Data
public class FlowDeviceTaskListByAreaReqVO {

    @Schema(description = "区域/工位编码，对应 flow_device_task.device_code", name = "Area")
    @JsonProperty("Area")
    @NotBlank(message = "区域编码不能为空")
    @Size(max = 64, message = "区域编码长度不能超过 64 个字符")
    private String area;

}
