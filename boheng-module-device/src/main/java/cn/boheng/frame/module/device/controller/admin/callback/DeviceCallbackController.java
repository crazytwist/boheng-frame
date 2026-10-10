package cn.boheng.frame.module.device.controller.admin.callback;

import cn.boheng.frame.framework.apilog.core.annotation.ApiAccessLog;
import cn.boheng.frame.framework.tenant.core.aop.TenantIgnore;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackReceipt;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackService;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备指令回调的统一入口。
 *
 * 设备不登录、不带租户头，靠命令号和一次性令牌认人。正文原样收下，交给动作上的解析规则处理。
 */
@Tag(name = "设备指令回调")
@RestController
@RequestMapping("/device/callback")
public class DeviceCallbackController {

    /**
     * 回调处理
     */
    @Resource
    private DeviceCallbackService deviceCallbackService;

    /**
     * 回调正文上限 1 MB，超出直接拒绝，避免撑爆原始数据表
     */
    private static final int MAX_BODY_LENGTH = 1024 * 1024;

    @PostMapping("/{commandNo}")
    @PermitAll
    @TenantIgnore
    @ApiAccessLog(sanitizeKeys = {"token", "callbackToken"})
    @Operation(summary = "设备按命令号回调结果", description = "令牌放在 token 参数或 X-Callback-Token 请求头")
    public ResponseEntity<String> receive(
            @Parameter(description = "命令号") @PathVariable("commandNo") String commandNo,
            @Parameter(description = "回调令牌") @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = DeviceCallbackService.TOKEN_HEADER, required = false) String tokenHeader,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody(required = false) String body) {
        if (tooLarge(body)) {
            return write(new DeviceCallbackReceipt(413, "回调正文超过 1 MB", commandNo));
        }
        String realToken = StrUtil.blankToDefault(token, tokenHeader);
        return write(deviceCallbackService.receive(commandNo, realToken, body, contentType));
    }

    @PostMapping
    @PermitAll
    @TenantIgnore
    @ApiAccessLog(sanitizeKeys = {"token", "callbackToken"})
    @Operation(summary = "设备回调结果（命令号放在请求头或正文）",
            description = "命令号依次认 commandNo 参数、X-Command-No 请求头、正文 JSON 的 commandNo；令牌依次认 token 参数、X-Callback-Token 请求头、正文 JSON 的 callbackToken")
    public ResponseEntity<String> receiveFixed(
            @Parameter(description = "命令号") @RequestParam(value = "commandNo", required = false) String commandNo,
            @Parameter(description = "回调令牌") @RequestParam(value = "token", required = false) String token,
            @RequestHeader(value = DeviceCallbackService.COMMAND_HEADER, required = false) String commandHeader,
            @RequestHeader(value = DeviceCallbackService.TOKEN_HEADER, required = false) String tokenHeader,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody(required = false) String body) {
        if (tooLarge(body)) {
            return write(new DeviceCallbackReceipt(413, "回调正文超过 1 MB", commandNo));
        }
        JSONObject fields = body != null && JSONUtil.isTypeJSONObject(body.trim()) ? JSONUtil.parseObj(body) : new JSONObject();
        String realNo = StrUtil.blankToDefault(StrUtil.blankToDefault(commandNo, commandHeader), fields.getStr("commandNo"));
        String realToken = StrUtil.blankToDefault(StrUtil.blankToDefault(token, tokenHeader), fields.getStr("callbackToken"));
        return write(deviceCallbackService.receive(realNo, realToken, body, contentType));
    }

    /**
     * 正文是否超过上限
     */
    private static boolean tooLarge(String body) {
        return body != null && body.length() > MAX_BODY_LENGTH;
    }

    /**
     * 答复写成简单 JSON，HTTP 状态码直接表达结果，方便设备判断要不要重发
     */
    private ResponseEntity<String> write(DeviceCallbackReceipt receipt) {
        JSONObject body = new JSONObject();
        body.set("code", receipt.httpStatus() == 200 ? 0 : receipt.httpStatus());
        body.set("msg", receipt.message());
        body.set("commandNo", receipt.commandNo());
        return ResponseEntity.status(receipt.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString());
    }

}
