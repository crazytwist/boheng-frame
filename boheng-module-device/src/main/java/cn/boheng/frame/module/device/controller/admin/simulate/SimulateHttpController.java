package cn.boheng.frame.module.device.controller.admin.simulate;

import cn.boheng.frame.framework.apilog.core.annotation.ApiAccessLog;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackService;
import cn.boheng.frame.module.device.service.simulate.SimulateAccessGuard;
import cn.boheng.frame.module.device.service.simulate.SimulateDeviceReply;
import cn.boheng.frame.module.device.service.simulate.SimulateHttpClient;
import cn.boheng.frame.module.device.service.simulate.SimulateHttpDeviceService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * 仿真设备暴露出来的 HTTP。
 *
 * 只给本进程调用：免登录，但必须带启动时生成的口令。报文不加 CommonResult 包装，和真仪器的原文响应一致。
 */
@Hidden
@RestController
@RequestMapping("/device/simulate/http")
public class SimulateHttpController {

    /**
     * 仿真设备的应答逻辑
     */
    @Resource
    private SimulateHttpDeviceService simulateHttpDeviceService;

    /**
     * 仿真接口口令
     */
    @Resource
    private SimulateAccessGuard accessGuard;

    @PostMapping("/{deviceCode}/{actionCode}")
    @PermitAll
    @ApiAccessLog(enable = false)
    public ResponseEntity<String> invoke(
            @Parameter(description = "设备编码") @PathVariable("deviceCode") String deviceCode,
            @Parameter(description = "动作编码") @PathVariable("actionCode") String actionCode,
            @RequestHeader(value = SimulateAccessGuard.HEADER, required = false) String secret,
            @RequestHeader(value = DeviceCallbackService.COMMAND_HEADER, required = false) String commandNo,
            @RequestHeader(value = DeviceCallbackService.CALLBACK_URL_HEADER, required = false) String callbackUrl,
            @RequestHeader(value = DeviceCallbackService.TOKEN_HEADER, required = false) String callbackToken,
            @RequestBody(required = false) String body) {
        if (!accessGuard.allows(secret)) {
            return forbidden();
        }
        return write(simulateHttpDeviceService.invoke(deviceCode, actionCode, body, commandNo, callbackUrl, callbackToken));
    }

    @GetMapping("/{deviceCode}/result/{jobId}")
    @PermitAll
    @ApiAccessLog(enable = false)
    public ResponseEntity<String> poll(
            @Parameter(description = "设备编码") @PathVariable("deviceCode") String deviceCode,
            @Parameter(description = "作业号") @PathVariable("jobId") String jobId,
            @RequestHeader(value = SimulateAccessGuard.HEADER, required = false) String secret) {
        if (!accessGuard.allows(secret)) {
            return forbidden();
        }
        return write(simulateHttpDeviceService.poll(deviceCode, jobId));
    }

    /**
     * 口令不对时拒绝，不透露任何配置
     */
    private ResponseEntity<String> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":\"FAILED\",\"message\":\"仅限平台内部调用\"}");
    }

    /**
     * 把仿真应答写成原始 HTTP，阶段和作业号放在响应头
     */
    private ResponseEntity<String> write(SimulateDeviceReply reply) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(reply.httpStatus())
                .contentType(MediaType.parseMediaType(reply.contentType()))
                .header(SimulateHttpClient.PHASE_HEADER, reply.phase());
        if (reply.jobId() != null) {
            builder.header(SimulateHttpClient.JOB_HEADER, reply.jobId());
        }
        return builder.body(reply.body());
    }

}
