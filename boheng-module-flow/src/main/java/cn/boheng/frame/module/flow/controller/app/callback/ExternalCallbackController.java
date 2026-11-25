package cn.boheng.frame.module.flow.controller.app.callback;

import cn.boheng.frame.framework.apilog.core.annotation.ApiAccessLog;
import cn.boheng.frame.framework.tenant.core.aop.TenantIgnore;
import cn.boheng.frame.module.flow.controller.app.callback.vo.ExternalCallbackReqVO;
import cn.boheng.frame.module.flow.engine.signal.FlowSignalDispatcher;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

/**
 * 节点级外部回调入口。
 *
 * <p>哪些回调走这里：认流程、能说清「哪次执行哪一步」的对方（LIMS / MES / 上游调度）。
 * 设备不认流程，它的回调整在设备域（{@code /api/callback/{commandNo}}），
 * 由完成事件驱动节点，不要混用。</p>
 *
 * <p>调用方不带租户头，靠执行编号 + 命中节点的令牌认人；
 * 等待行的定位与结算全部在流程域内完成，不经过设备域。</p>
 */
@Tag(name = "App - 流程外部回调")
@RestController
@RequestMapping("/flow/external-callback")
@Validated
public class ExternalCallbackController {

    /**
     * 信号分发：定位等待行、校验令牌、结算节点
     */
    @Resource
    private FlowSignalDispatcher signalDispatcher;

    @PostMapping("/{executionId}/{nodeId}")
    @PermitAll
    @TenantIgnore
    @ApiAccessLog(sanitizeKeys = {"token"})
    @Operation(summary = "外部系统回报节点结果",
            description = "令牌放在 token 参数、X-Callback-Token 请求头或正文 token 字段")
    public ResponseEntity<String> receive(
            @Parameter(description = "流程执行编号") @PathVariable("executionId") Long executionId,
            @Parameter(description = "节点编码") @PathVariable("nodeId") String nodeId,
            @Parameter(description = "回调令牌") @RequestParam(value = "token", required = false) String token,
            @Parameter(description = "第几次尝试") @RequestParam(value = "attempt", required = false) Integer attempt,
            @RequestHeader(value = "X-Callback-Token", required = false) String tokenHeader,
            @Valid @RequestBody ExternalCallbackReqVO reqVO) {
        String realToken = StrUtil.blankToDefault(StrUtil.blankToDefault(token, tokenHeader), reqVO.getToken());
        Integer realAttempt = attempt != null ? attempt : reqVO.getAttempt();
        boolean accepted = signalDispatcher.externalCallback(executionId, nodeId, realAttempt, realToken,
                Boolean.TRUE.equals(reqVO.getSuccess()), reqVO.getResultJson(), reqVO.getErrorMessage());
        if (!accepted) {
            // 不细分原因：对外只回答「这次回调没有生效」，细节留在服务端日志
            return write(409, "节点不在等待、令牌不匹配或已结算过，本次回调没有写入", executionId, nodeId);
        }
        return write(200, "ok", executionId, nodeId);
    }

    /**
     * 答复写成简单 JSON，HTTP 状态码表达结果，方便对方判断要不要重发
     */
    private ResponseEntity<String> write(int httpStatus, String message, Long executionId, String nodeId) {
        JSONObject body = new JSONObject();
        body.set("code", httpStatus == 200 ? 0 : httpStatus);
        body.set("msg", message);
        body.set("executionId", executionId);
        body.set("nodeId", nodeId);
        return ResponseEntity.status(httpStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.toString());
    }

}
