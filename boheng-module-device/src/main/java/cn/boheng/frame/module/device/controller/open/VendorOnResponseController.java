package cn.boheng.frame.module.device.controller.open;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.tenant.core.aop.TenantIgnore;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackReceipt;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackService;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import static cn.boheng.frame.framework.common.pojo.CommonResult.error;
import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 厂商异步回调的固定地址入口。
 *
 * <p>厂商协议里回调地址是固定的 {@code /api/OnResponse}，不带平台自己的
 * {@code /admin-api} 前缀，所以这个类刻意放在 {@code controller.open} 包下——
 * 平台的前缀是按包名 {@code **.controller.admin.**} 匹配的，放在这里就不会被改写。</p>
 *
 * <p>有些设备（如海康 RCS）没法把带令牌的回调地址传过去：它的请求报文是固定的几个字段，
 * 里面没有放回调地址的位置。这类设备只能在厂商侧配一个固定地址，回调时带着
 * 「我们发给它、它原样带回来」的业务字段（如 reqCode）。这里就是收这种回调的地方，
 * 靠 {@code device_command.correlate_value} 认命令。</p>
 *
 * <p>响应约定：HTTP 状态码如实反映处理结果（200 收下 / 400 报文认不出 / 404 没匹配到 /
 * 409 命令已结束），厂商靠它决定要不要重发；响应体仍是平台统一的 {@link CommonResult}。
 * 这一点与 {@code DeviceCallbackController} 一致，都是「让对方能判断要不要重试」的场合。</p>
 *
 * @author yinan
 */
@Tag(name = "厂商回调（固定地址）")
@RestController
@RequestMapping("/api")
@Slf4j
public class VendorOnResponseController {

    /**
     * 正文打印上限。厂商回包一般只有几百字节，超长多半是打错了地址，截断避免刷爆日志
     */
    private static final int LOG_BODY_LIMIT = 8 * 1024;

    /**
     * 按关联键认命令并结算
     */
    @Resource
    private DeviceCallbackService deviceCallbackService;

    /**
     * 收下厂商回调并交给命令结算。
     *
     * @param body 原始报文，不做反序列化，保证字段一个不丢
     * @param contentType 厂商声明的类型，可能是 application/json 也可能带 charset
     * @param request 用来记下来源地址，排查「到底是谁打进来的」
     */
    @PostMapping("/OnResponse")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "接收厂商异步回调（固定地址）",
            description = "按报文里的关联键定位命令并结算，命中后驱动等待中的流程节点继续")
    public ResponseEntity<CommonResult<String>> onResponse(
            @RequestBody(required = false) String body,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            HttpServletRequest request) {
        log.info("""
                        [OnResponse][收到厂商回调]
                          来源   = {} {} from {}
                          类型   = {}
                          长度   = {}
                          报文   = {}""",
                request.getMethod(), request.getRequestURI(), request.getRemoteAddr(),
                StrUtil.blankToDefault(contentType, "未声明"),
                body == null ? 0 : body.length(),
                abbreviate(body));

        DeviceCallbackReceipt receipt = deviceCallbackService.receiveByCorrelate(body, contentType);
        if (receipt.httpStatus() == 200) {
            return ResponseEntity.ok(success("ok"));
        }
        log.warn("[OnResponse][回调未生效] 状态={} 说明={} 命令号={}",
                receipt.httpStatus(), receipt.message(), receipt.commandNo());
        return ResponseEntity.status(receipt.httpStatus())
                .body(error(receipt.httpStatus(), receipt.message()));
    }

    /**
     * 打印用：空报文给个明确标记，超长截断
     */
    private static String abbreviate(String body) {
        if (StrUtil.isBlank(body)) {
            return "<空>";
        }
        String trimmed = body.trim();
        return trimmed.length() <= LOG_BODY_LIMIT ? trimmed
                : trimmed.substring(0, LOG_BODY_LIMIT) + "...(已截断，原长 " + trimmed.length() + ")";
    }


}
