package cn.boheng.frame.module.device.controller.open;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.tenant.core.aop.TenantIgnore;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackReceipt;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackService;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.boheng.frame.framework.common.pojo.CommonResult.error;
import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * AGV（海康 RCS）异步回调的固定地址入口。
 *
 * <p>RCS 的回调地址在厂商侧写死，报文里既没有平台命令号也没有令牌，
 * 只有它原样带回来的 {@code reqCode}——那是我们下发时现生成、写进
 * {@code device_command.correlate_value} 的流水号。所以这里只做三件事：</p>
 * <ol>
 *   <li>按 {@code reqCode} 找回还在等回调的那条命令</li>
 *   <li>判断这条回调是不是终态（{@code method=end}）——中途回调只回执，不结算</li>
 *   <li>结算：写结果 → 发布完成事件 → 等待中的流程节点被唤醒继续跑</li>
 * </ol>
 *
 * <p><b>结算之后流程怎么往下走：</b>命令落到 SUCCEEDED/FAILED 时会发
 * {@code DeviceCommandCompletedEvent}，流程域的 {@code FlowSignalDispatcher} 按 commandId
 * 找到挂起的节点并推进。控制器不需要、也不应该自己去调流程接口——
 * 回调只负责「把设备这一句话记准」。</p>
 *
 * <p><b>响应约定：</b>HTTP 状态码如实反映处理结果（200 收下 / 400 报文为空或认不出 /
 * 404 没有在等的命令 / 409 命令已结束），厂商据此决定要不要重发；响应体仍是平台统一的
 * {@link CommonResult}。与 {@link VendorOnResponseController}、{@link DeviceCallbackController}
 * 三处保持一致。</p>
 *
 * <p>与 HPLC 的分工：HPLC 的回调地址也是厂商写死的，但它把地址写成 {@code /api/OnResponse}，
 * 所以走 {@link VendorOnResponseController}；AGV 走这里。两者底层是同一条结算链路。</p>
 *
 * @author yinan
 */
@Slf4j
@Tag(name = "AGV 指令回调")
@RestController
@RequestMapping("/api")
public class AgvTaskCallbackController {

    /**
     * 回调处理
     */
    @Resource
    private DeviceCallbackService deviceCallbackService;

    /**
     * 日志里报文最多打这么长，避免厂商回大报文把日志撑爆
     */
    private static final int LOG_BODY_LIMIT = 4 * 1024;

    @PostMapping("/callback")
    @PermitAll
    @TenantIgnore
    @Operation(summary = "接收 AGV 异步回调（固定地址）",
            description = "按报文里的 reqCode 定位命令并结算；只有 method=end 才结算，"
                    + "结算后发布命令完成事件，等待中的流程节点被唤醒继续")
    public ResponseEntity<CommonResult<String>> agvCallback(
            @RequestBody(required = false) AgvTaskCallbackReqDTO reqDTO) {
        if (reqDTO == null) {
            return reply(new DeviceCallbackReceipt(400, "回调报文为空", null));
        }
        String body = JSONUtil.toJsonStr(reqDTO);
        log.info("[AgvCallback][收到回调] reqCode={} method={} taskCode={} robotCode={} 报文={}",
                reqDTO.getReqCode(), reqDTO.getMethod(), reqDTO.getTaskCode(), reqDTO.getRobotCode(),
                abbreviate(body));
        // 用 DTO 里的 reqCode 精确认人：这是唯一能对上号的字段。
        // taskCode 是厂商自己的任务号（和我们下发的 data 不是一回事），不能拿来关联。
        return reply(deviceCallbackService.receiveByCorrelateKey(reqDTO.getReqCode(), body,
                MediaType.APPLICATION_JSON_VALUE));
    }

    /**
     * 把处理结果翻译成 HTTP 响应。非 200 的状态码是有意的：厂商靠它判断要不要重发
     */
    private ResponseEntity<CommonResult<String>> reply(DeviceCallbackReceipt receipt) {
        if (receipt.httpStatus() == 200) {
            // 结算成功时 message 是 ok；中途回调时是「尚未结束」的说明，回给厂商便于对方排障
            return ResponseEntity.ok(success(receipt.message()));
        }
        log.warn("[AgvCallback][回调未生效] 状态={} 说明={} 命令号={}",
                receipt.httpStatus(), receipt.message(), receipt.commandNo());
        return ResponseEntity.status(receipt.httpStatus())
                .body(error(receipt.httpStatus(), receipt.message()));
    }

    /**
     * 报文截断，只在日志里用
     */
    private static String abbreviate(String body) {
        if (StrUtil.isBlank(body)) {
            return "<空>";
        }
        return body.length() <= LOG_BODY_LIMIT ? body : body.substring(0, LOG_BODY_LIMIT) + "...(已截断)";
    }

}
