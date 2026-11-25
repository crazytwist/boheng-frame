//package cn.boheng.frame.module.device.controller.open;
//
//import cn.boheng.frame.framework.apilog.core.annotation.ApiAccessLog;
//import cn.boheng.frame.framework.tenant.core.aop.TenantIgnore;
//import cn.boheng.frame.module.device.service.callback.DeviceCallbackReceipt;
//import cn.boheng.frame.module.device.service.callback.DeviceCallbackService;
//import cn.hutool.core.util.StrUtil;
//import cn.hutool.json.JSONObject;
//import cn.hutool.json.JSONUtil;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.Parameter;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import jakarta.annotation.Resource;
//import jakarta.annotation.security.PermitAll;
//import org.springframework.http.MediaType;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.PathVariable;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestHeader;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
///**
// * 设备回调的统一入口（{@code /api/callback}）。
// *
// * <p>设备不登录、不带租户头。正文原样收下，交给动作上的解析规则处理。</p>
// *
// * <p>放 {@code controller.open} 包下是刻意的：设备是外部系统，回调不该走
// * {@code /admin-api} 这个管理端前缀。平台的前缀按包名 {@code **.controller.admin.**}
// * 匹配，放这里路径就保持 {@code /api/callback/...} 原样。</p>
// *
// * <p><b>两种设备、两种认人方式，都收在这一个入口：</b></p>
// * <ol>
// *   <li><b>回调地址由我们签发</b>（{@code POST /api/callback/{commandNo}?token=...}）：
// *       命令号是我们拼进地址里的，令牌也在地址里，认人最准。设备不需要「知道」命令号，
// *       它只是把收到的地址原样调回去。</li>
// *   <li><b>回调地址由厂商写死</b>（{@code POST /api/callback}，如 AGV/RCS、HPLC）：
// *       报文里没有命令号，也没有令牌。这里按报文内容去认人——
// *       拿我们下发时记下的关联键（如 {@code reqCode}）反查「还在等回调」的那条命令。</li>
// * </ol>
// *
// * <p>{@link VendorOnResponseController} 是同一套逻辑的另一个地址：厂商协议把路径写死成
// * {@code /api/OnResponse} 时用那个，能力完全一样。</p>
// *
// * @author yinan
// */
//@Tag(name = "设备指令回调")
//@RestController
//@RequestMapping("/api/callback")
//public class DeviceCallbackController {
//
//    /**
//     * 回调处理
//     */
//    @Resource
//    private DeviceCallbackService deviceCallbackService;
//
//    /**
//     * 回调正文上限 1 MB，超出直接拒绝，避免撑爆原始数据表
//     */
//    private static final int MAX_BODY_LENGTH = 1024 * 1024;
//
//    @PostMapping("/{commandNo}")
//    @PermitAll
//    @TenantIgnore
//    @ApiAccessLog(sanitizeKeys = {"token", "callbackToken"})
//    @Operation(summary = "设备按命令号回调结果", description = "命令号在路径里，令牌放在 token 参数或 X-Callback-Token 请求头；"
//            + "仅用于我们把带命令号的回调地址发给设备的场景")
//    public ResponseEntity<String> receive(
//            @Parameter(description = "命令号") @PathVariable("commandNo") String commandNo,
//            @Parameter(description = "回调令牌") @RequestParam(value = "token", required = false) String token,
//            @RequestHeader(value = DeviceCallbackService.TOKEN_HEADER, required = false) String tokenHeader,
//            @RequestHeader(value = "Content-Type", required = false) String contentType,
//            @RequestBody(required = false) String body) {
//        if (tooLarge(body)) {
//            return write(new DeviceCallbackReceipt(413, "回调正文超过 1 MB", commandNo));
//        }
//        String realToken = StrUtil.blankToDefault(token, tokenHeader);
//        return write(deviceCallbackService.receive(commandNo, realToken, body, contentType));
//    }
//
//    @PostMapping
//    @PermitAll
//    @TenantIgnore
//    @ApiAccessLog(sanitizeKeys = {"token", "callbackToken"})
//    @Operation(summary = "设备回调结果（命令号可有可无）",
//            description = "给得出命令号时依次认 commandNo 参数、X-Command-No 请求头、正文 JSON 的 commandNo，走令牌校验；"
//                    + "给不出命令号时按报文内容（下发时记下的关联键，如 reqCode）反查还在等回调的命令")
//    public ResponseEntity<String> receiveFixed(
//            @Parameter(description = "命令号") @RequestParam(value = "commandNo", required = false) String commandNo,
//            @Parameter(description = "回调令牌") @RequestParam(value = "token", required = false) String token,
//            @RequestHeader(value = DeviceCallbackService.COMMAND_HEADER, required = false) String commandHeader,
//            @RequestHeader(value = DeviceCallbackService.TOKEN_HEADER, required = false) String tokenHeader,
//            @RequestHeader(value = "Content-Type", required = false) String contentType,
//            @RequestBody(required = false) String body) {
//        if (tooLarge(body)) {
//            return write(new DeviceCallbackReceipt(413, "回调正文超过 1 MB", commandNo));
//        }
//        JSONObject fields = body != null && JSONUtil.isTypeJSONObject(body.trim()) ? JSONUtil.parseObj(body) : new JSONObject();
//        String realNo = StrUtil.blankToDefault(StrUtil.blankToDefault(commandNo, commandHeader), fields.getStr("commandNo"));
//        if (StrUtil.isBlank(realNo)) {
//            // 厂商写死地址的设备（AGV / HPLC）压根不知道命令号是什么，
//            // 报文里也不会带。这种情况不能回 400，得按报文内容去认人。
//            return write(deviceCallbackService.receiveByCorrelate(body, contentType));
//        }
//        String realToken = StrUtil.blankToDefault(StrUtil.blankToDefault(token, tokenHeader), fields.getStr("callbackToken"));
//        return write(deviceCallbackService.receive(realNo, realToken, body, contentType));
//    }
//
//    /**
//     * 正文是否超过上限
//     */
//    private static boolean tooLarge(String body) {
//        return body != null && body.length() > MAX_BODY_LENGTH;
//    }
//
//    /**
//     * 答复写成简单 JSON，HTTP 状态码直接表达结果，方便设备判断要不要重发
//     */
//    private ResponseEntity<String> write(DeviceCallbackReceipt receipt) {
//        JSONObject body = new JSONObject();
//        body.set("code", receipt.httpStatus() == 200 ? 0 : receipt.httpStatus());
//        body.set("msg", receipt.message());
//        body.set("commandNo", receipt.commandNo());
//        return ResponseEntity.status(receipt.httpStatus())
//                .contentType(MediaType.APPLICATION_JSON)
//                .body(body.toString());
//    }
//
//}
