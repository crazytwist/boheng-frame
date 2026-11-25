package cn.boheng.frame.module.device.service.http;

import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeReqVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.mysql.action.DeviceActionMapper;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackService;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackTicket;
import cn.boheng.frame.module.device.service.action.DeviceParamResolver;
import cn.boheng.frame.module.device.service.command.DeviceCommandService;
import cn.boheng.frame.module.device.service.device.DeviceInfoService;
import cn.boheng.frame.module.device.service.result.DeviceResultJudge;
import cn.boheng.frame.module.device.service.result.ResultVerdict;
import cn.boheng.frame.module.device.service.simulate.SimulateHttpService;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.DeviceEnums.CommandStatusEnum;
import static cn.boheng.frame.module.device.enums.DeviceEnums.ConnectionTypeEnum;
import static cn.boheng.frame.module.device.enums.DeviceEnums.DeviceStatusEnum;
import static cn.boheng.frame.module.device.enums.DeviceEnums.ResultModeEnum;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_DISABLED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_NOT_EXISTS;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACTION_PATH_MISSING;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_ACCEPT_REJECTED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_CALLBACK_DISABLED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_HTTP_FAILED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_NOT_EXISTS;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_UNAVAILABLE;

/**
 * 按台账和动作发起一次 HTTP 调用。
 */
@Service
@Slf4j
@Validated
public class DeviceHttpService {
    /**
     * 设备台账服务
     */

    @Resource
    private DeviceInfoService deviceInfoService;
    /**
     * 设备动作表
     */
    @Resource
    private DeviceActionMapper deviceActionMapper;
    /**
     * 设备 HTTP 访问
     */
    @Resource
    private DeviceHttpGateway deviceHttpGateway;
    /**
     * 命令记录服务
     */
    @Resource
    private DeviceCommandService deviceCommandService;
    /**
     * 仿真 HTTP。台账标了仿真，或动作配了仿真报文时走这里
     */
    @Resource
    private SimulateHttpService simulateHttpService;
    /**
     * 设备回调。动作要等回调时签发回调地址并等待
     */
    @Resource
    private DeviceCallbackService deviceCallbackService;
    /**
     * 设备动作输入契约解析器，统一展开默认值并校验最终参数
     */
    @Resource
    private DeviceParamResolver deviceParamResolver;
    /**
     * 应答判定器。受理应答和最终应答用同一套 result_rule，判成败只有一处实现
     */
    @Resource
    private DeviceResultJudge deviceResultJudge;

    /**
     * 校验设备和动作后发起调用，并记下命令。来源按人工测试记录
     */
    public DeviceInvokeRespVO invoke(DeviceInvokeReqVO reqVO) {
        return invoke(reqVO, null);
    }

    /**
     * 校验设备和动作后发起调用，并记下命令。
     *
     * @param source 命令来源。流程引擎下发时传 FLOW 来源（来源类型 / 来源引用 / 尝试次数），
     *               事后能沿 source_ref 找回是哪个运行哪个节点哪次尝试发的；为空按人工测试记
     */
    public DeviceInvokeRespVO invoke(DeviceInvokeReqVO reqVO, DeviceCommandService.CommandSource source) {
        DeviceInfoDO device = deviceInfoService.getDevice(reqVO.getDeviceId());
        if (device == null) {
            throw exception(DEVICE_NOT_EXISTS);
        }
        DeviceActionDO action = deviceActionMapper.selectByTypeAndAction(device.getDeviceTypeCode(), reqVO.getActionCode());
        if (action == null) {
            throw exception(DEVICE_ACTION_NOT_EXISTS);
        }
        if (action.getStatus() != null && action.getStatus() != 0) {
            throw exception(DEVICE_ACTION_DISABLED, action.getActionCode());
        }
        boolean simulate = simulateHttpService.supports(device, action.getActionCode());
        if (!simulate && (DeviceStatusEnum.MAINTENANCE.getStatus().equals(device.getStatus())
                || DeviceStatusEnum.FAULT.getStatus().equals(device.getStatus()))) {
            throw exception(DEVICE_UNAVAILABLE, device.getDeviceCode(),
                    DeviceStatusEnum.MAINTENANCE.getStatus().equals(device.getStatus())
                            ? DeviceStatusEnum.MAINTENANCE.getName()
                            : DeviceStatusEnum.FAULT.getName());
        }
        if (!simulate) {
            String connection = device.getConnectionType();
            if (!ConnectionTypeEnum.HTTP.getType().equals(connection)
                    && !ConnectionTypeEnum.NODE_RED.getType().equals(connection)) {
                throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(), "接入方式不是 HTTP");
            }
            if (StrUtil.isBlank(action.getRequestPath())) {
                throw exception(DEVICE_ACTION_PATH_MISSING, action.getActionCode());
            }
        }
        boolean callback = simulate
                ? simulateHttpService.expectsCallback(device, action.getActionCode())
                : ResultModeEnum.CALLBACK.getMode().equals(action.getResultMode());
        if (callback && !simulate && !Boolean.TRUE.equals(device.getCallbackEnabled())) {
            throw exception(DEVICE_CALLBACK_DISABLED, device.getDeviceCode());
        }
        // 流程域传了值就用传进来的，没传才由契约默认兜底；两个来源都记下来，事后能回答「这个值是谁定的」
        DeviceParamResolver.ResolvedParams resolved =
                deviceParamResolver.resolveWithSource(action.getParamSchema(), reqVO.getParamsJson());
        String resolvedParamsJson = resolved.paramsJson();
        String paramsSourceJson = resolved.sources().isEmpty() ? null : JSONUtil.toJsonStr(resolved.sources());
        String codecCode = StrUtil.blankToDefault(reqVO.getCodecCode(), action.getCodecCode());
        DeviceCommandDO command = deviceCommandService.open(device, action, resolvedParamsJson, codecCode, source,
                paramsSourceJson);
        DeviceCallbackTicket ticket = null;
        try {
            if (callback) {
                ticket = deviceCallbackService.issue(device, action, command.getCommandNo(), simulate);
                deviceCommandService.markWaitingCallback(command.getId(), ticket.tokenHash(), ticket.timeoutAt());
            }
            DeviceInvokeRespVO resp = simulate
                    ? simulateHttpService.exchange(device, action, resolvedParamsJson, command.getId(),
                            command.getCommandNo(), ticket)
                    : deviceHttpGateway.invoke(device, action, resolvedParamsJson, command.getCommandNo(), ticket);
            // 报文已经渲染出来了，趁现在把固定地址回调的关联键存下来。
            // 必须在下发之后做：这个值只有渲染完才知道（比如模板里的 reqCode 是现生成的）。
            bindCorrelateValue(command.getId(), action, resp);
            if (callback) {
                acceptCallback(device, action, command, resp);
                resp.setStatus(202);
                resp.setCommandStatus(CommandStatusEnum.WAITING.getStatus());
            } else {
                // 同步路径：成败由设备域按厂商协议判定，HTTP 码只是其中一项
                ResultVerdict verdict = deviceCommandService.complete(command.getId(), resp, codecCode);
                resp.setCommandStatus(verdict.success() ? CommandStatusEnum.SUCCEEDED.getStatus()
                        : CommandStatusEnum.FAILED.getStatus());
                resp.setErrorType(verdict.errorType());
                resp.setRetryable(verdict.retryable());
            }
            resp.setCommandId(command.getId());
            resp.setCommandNo(command.getCommandNo());
            return resp;
        } catch (RuntimeException ex) {
            deviceCommandService.fail(command.getId(), ex.getMessage());
            throw ex;
        }
    }

    /**
     * 记录固定地址回调的关联键值。
     *
     * <p>动作上没配 {@code correlatePath} 就直接跳过——那种动作走带命令号的回调地址，
     * 不需要关联键。配了但报文里取不到值时只告警不失败：命令该下发还是下发，
     * 只是这单的固定地址回调会认不出来，属于配置问题，不该拖累业务。</p>
     *
     * <p>{@code correlatePath} 可以写多个，逗号分隔，按顺序取第一个有值的。
     * 典型用法是 HPLC 那种「EventId 和 RequestId 都是我们现生成的」——
     * 关联键列只存得下一个，另一个靠回调时的报文兜底匹配认。</p>
     */
    private void bindCorrelateValue(Long commandId, DeviceActionDO action, DeviceInvokeRespVO resp) {
        String paths = StrUtil.trimToNull(action.getCorrelatePath());
        if (paths == null || resp == null) {
            return;
        }
        String body = StrUtil.trimToNull(resp.getRequestBody());
        if (body == null) {
            return;
        }
        final JSONObject payload;
        try {
            payload = JSONUtil.parseObj(body);
        } catch (RuntimeException ex) {
            log.warn("[bindCorrelateValue][命令({}) 报文不是 JSON，取不到关联键 {}]", commandId, paths);
            return;
        }
        for (String path : StrUtil.split(paths, ',')) {
            String single = StrUtil.trimToNull(path);
            if (single == null) {
                continue;
            }
            Object value = payload.getByPath(single);
            if (value == null || StrUtil.isBlank(String.valueOf(value))) {
                continue;
            }
            deviceCommandService.bindCorrelateValue(commandId, String.valueOf(value));
            return;
        }
        log.warn("[bindCorrelateValue][命令({}) 报文里没有关联键 {}，固定地址回调将认不出这条命令]", commandId, paths);
    }

    /**
     * 记录设备受理应答。受理成功后调用方立即返回，命令继续保持等待回调状态。
     */
    /**
     * 判「设备到底接没接这单」。
     *
     * <p>不能只看 HTTP 码：厂商常见做法是 HTTP 200 + 业务码说明拒绝
     * （AGV 回 {@code {"code":1,"message":"任务已满"}}，HPLC 回 {@code {"IsAccepted":false}}）。
     * 这种应答如果按「已受理」放过去，命令会停在等回调，永远等不到，最后表现成超时。
     * 所以受理应答也走和设备应答同一套 {@code result_rule} 判定。</p>
     *
     * <p>拒绝时把命令直接收口成失败（带设备声明的失败分类和可重试性），并把错误抛给调用方。</p>
     */
    private void acceptCallback(DeviceInfoDO device, DeviceActionDO action, DeviceCommandDO command,
                                DeviceInvokeRespVO ack) {
        deviceCommandService.recordSent(command.getId(), ack);
        ResultVerdict verdict = deviceResultJudge.judge(action, ack.getBody(), ack.getStatus());
        if (verdict.success()) {
            return;
        }
        deviceCommandService.fail(command.getId(), verdict);
        log.warn("[acceptCallback][设备({}) 命令({}) 未受理：{}]", device.getDeviceCode(), command.getCommandNo(),
                verdict.message());
        throw exception(DEVICE_ACCEPT_REJECTED, device.getDeviceCode(),
                StrUtil.blankToDefault(verdict.message(), "HTTP " + ack.getStatus()));
    }

}
