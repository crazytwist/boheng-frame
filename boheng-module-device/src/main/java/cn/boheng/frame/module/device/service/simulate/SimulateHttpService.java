package cn.boheng.frame.module.device.service.simulate;

import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.dataobject.simulate.DeviceSimulateProfileDO;
import cn.boheng.frame.module.device.enums.SimulateReturnModeEnum;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackService;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackTicket;
import cn.boheng.frame.module.device.service.callback.DevicePlatformUrl;
import cn.boheng.frame.module.device.service.command.DeviceCommandService;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_HTTP_FAILED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_SIMULATE_POLL_TIMEOUT;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_SIMULATE_PROFILE_NOT_EXISTS;

/**
 * 平台侧的仿真 HTTP 调用。
 *
 * 同步是一次 POST 直接拿结果。回调是一次 POST 拿受理，结果由仿真设备之后 POST 到回调地址，等待由调用方负责。
 * 轮询是先 POST 受理，再按配置的间隔 GET 结果，直到拿到最终报文或次数用尽。
 */
@Service
@Slf4j
public class SimulateHttpService {

    /**
     * 同步调用和单次轮询的 HTTP 超时
     */
    private static final int CALL_TIMEOUT_MS = 20_000;

    /**
     * 平台地址，仿真设备在本进程里
     */
    @Resource
    private DevicePlatformUrl platformUrl;

    /**
     * 仿真配置
     */
    @Resource
    private DeviceSimulateProfileService profileService;

    /**
     * 访问仿真设备的 HTTP 客户端
     */
    @Resource
    private SimulateHttpClient simulateHttpClient;

    /**
     * 命令记录，用来写下发方式和轮询次数
     */
    @Resource
    private DeviceCommandService deviceCommandService;

    /**
     * 这台设备的这次动作是否走仿真 HTTP。
     * 台账标了仿真，或这个动作有启用的仿真配置，都走这里。
     */
    public boolean supports(DeviceInfoDO device, String actionCode) {
        if (device == null) {
            return false;
        }
        if (marked(device)) {
            return true;
        }
        return profileService.getEnabled(device.getId(), actionCode) != null;
    }

    /**
     * 这次仿真是否要等仿真设备回调
     */
    public boolean expectsCallback(DeviceInfoDO device, String actionCode) {
        DeviceSimulateProfileDO profile = profileService.getEnabled(device.getId(), actionCode);
        return profile != null && SimulateReturnModeEnum.of(profile.getReturnMode()) == SimulateReturnModeEnum.CALLBACK;
    }

    /**
     * 按配置发 HTTP。同步和轮询返回最终报文；回调只返回受理应答。
     */
    public DeviceInvokeRespVO exchange(DeviceInfoDO device, DeviceActionDO action, String paramsJson, Long commandId,
                                       String commandNo, DeviceCallbackTicket ticket) {
        DeviceSimulateProfileDO profile = profileService.getEnabled(device.getId(), action.getActionCode());
        if (profile == null) {
            throw exception(DEVICE_SIMULATE_PROFILE_NOT_EXISTS);
        }
        SimulateReturnModeEnum mode = SimulateReturnModeEnum.of(profile.getReturnMode());
        if (mode == null) {
            throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(), "回收方式无法识别");
        }
        if (mode == SimulateReturnModeEnum.CALLBACK && ticket == null) {
            throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(), "回调方式缺少回调地址");
        }
        if (mode != SimulateReturnModeEnum.CALLBACK) {
            deviceCommandService.bindMode(commandId, mode == SimulateReturnModeEnum.SYNC ? "SYNC" : "ASYNC",
                    mode == SimulateReturnModeEnum.POLL ? "POLL" : "SYNC_RETURN");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(DeviceCallbackService.COMMAND_HEADER, commandNo);
        if (ticket != null) {
            headers.put(DeviceCallbackService.CALLBACK_URL_HEADER, ticket.url());
            headers.put(DeviceCallbackService.TOKEN_HEADER, ticket.token());
        }
        String requestBody = requestBody(device, action, profile, paramsJson, commandNo, ticket);
        String invokeUrl = invokeUrl(device.getDeviceCode(), action.getActionCode());
        log.info("[exchange][设备({}) 动作({}) 仿真 HTTP {}]", device.getDeviceCode(), action.getActionCode(), mode);
        SimulateHttpClient.SimulateHttpExchange first = simulateHttpClient.post(device.getDeviceCode(), invokeUrl,
                requestBody, CALL_TIMEOUT_MS, headers);
        ensureOk(device, first);
        if (mode != SimulateReturnModeEnum.POLL || finished(first)) {
            return toResp(first);
        }
        return poll(device, profile, commandId, first);
    }

    /**
     * 按间隔轮询，直到仿真设备给出最终报文
     */
    private DeviceInvokeRespVO poll(DeviceInfoDO device, DeviceSimulateProfileDO profile, Long commandId,
                                    SimulateHttpClient.SimulateHttpExchange accepted) {
        String jobId = jobIdOf(accepted);
        if (StrUtil.isBlank(jobId)) {
            throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(), "仿真设备没有返回作业号");
        }
        int interval = profile.getPollIntervalMs() == null ? 1000 : profile.getPollIntervalMs();
        int maxTimes = profile.getPollMaxTimes() == null ? 30 : profile.getPollMaxTimes();
        String pollUrl = pollUrl(device.getDeviceCode(), jobId);
        SimulateHttpClient.SimulateHttpExchange last = accepted;
        for (int i = 1; i <= maxTimes; i++) {
            if (!sleep(device, interval)) {
                throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(), "轮询等待被中断");
            }
            last = simulateHttpClient.get(device.getDeviceCode(), pollUrl);
            ensureOk(device, last);
            boolean done = finished(last);
            deviceCommandService.recordPoll(commandId, i, done);
            if (done) {
                return toResp(last, accepted);
            }
        }
        throw exception(DEVICE_SIMULATE_POLL_TIMEOUT, device.getDeviceCode(), maxTimes);
    }

    /**
     * 台账把这台设备标成仿真
     */
    private boolean marked(DeviceInfoDO device) {
        return "SIMULATED".equalsIgnoreCase(StrUtil.blankToDefault(device.getDriverType(), ""))
                || "SIMULATED".equalsIgnoreCase(StrUtil.blankToDefault(device.getConnectionType(), ""))
                || Boolean.TRUE.equals(device.getSimulationMode());
    }

    /**
     * 渲染请求报文。配置了模板就用模板，否则用动作上的模板，再没有就把参数原样发出。
     * 模板里还能用 ${commandNo}、${callbackUrl}、${callbackToken}。
     */
    private String requestBody(DeviceInfoDO device, DeviceActionDO action, DeviceSimulateProfileDO profile,
                               String paramsJson, String commandNo, DeviceCallbackTicket ticket) {
        Map<String, Object> params;
        try {
            params = SimulateMessageRenderer.paramsOf(paramsJson);
        } catch (IllegalArgumentException ex) {
            throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(), ex.getMessage());
        }
        String template = StrUtil.blankToDefault(profile.getRequestTemplate(), action.getRequestTemplate());
        if (StrUtil.isNotBlank(template)) {
            Map<String, Object> vars = new LinkedHashMap<>(params);
            vars.putIfAbsent("commandNo", commandNo);
            if (ticket != null) {
                vars.putIfAbsent("callbackUrl", ticket.url());
                vars.putIfAbsent("callbackToken", ticket.token());
            }
            return SimulateMessageRenderer.render(template, vars);
        }
        if (params.isEmpty()) {
            return "{}";
        }
        return JSONUtil.toJsonStr(params);
    }

    /**
     * 非 2xx 或设备明确拒绝时，把仿真设备的正文带进失败原因
     */
    private void ensureOk(DeviceInfoDO device, SimulateHttpClient.SimulateHttpExchange exchange) {
        boolean httpOk = exchange.status() >= 200 && exchange.status() < 300;
        boolean rejected = SimulateDeviceReply.FAILED.equalsIgnoreCase(exchange.phase());
        if (!httpOk || rejected) {
            throw exception(DEVICE_HTTP_FAILED, device.getDeviceCode(),
                    StrUtil.blankToDefault(exchange.body(), "HTTP " + exchange.status()));
        }
    }

    /**
     * 转成命令记录能收下的调用结果
     */
    private DeviceInvokeRespVO toResp(SimulateHttpClient.SimulateHttpExchange exchange) {
        return toResp(exchange, exchange);
    }

    /**
     * 轮询成功时，请求仍记下第一次下发，响应记下最后一次拿到的报文
     */
    private DeviceInvokeRespVO toResp(SimulateHttpClient.SimulateHttpExchange response,
                                      SimulateHttpClient.SimulateHttpExchange request) {
        DeviceInvokeRespVO resp = new DeviceInvokeRespVO();
        resp.setStatus(response.status());
        resp.setBody(response.body());
        resp.setContentType(response.contentType());
        resp.setRetriedLogin(false);
        resp.setHttpMethod(request.httpMethod());
        resp.setRequestUrl(request.requestUrl());
        resp.setRequestBody(request.requestBody());
        return resp;
    }

    /**
     * 下发地址
     */
    private String invokeUrl(String deviceCode, String actionCode) {
        return platformUrl.localAdminBase() + "/device/simulate/http/" + DevicePlatformUrl.encode(deviceCode)
                + "/" + DevicePlatformUrl.encode(actionCode);
    }

    /**
     * 轮询地址
     */
    private String pollUrl(String deviceCode, String jobId) {
        return platformUrl.localAdminBase() + "/device/simulate/http/" + DevicePlatformUrl.encode(deviceCode)
                + "/result/" + DevicePlatformUrl.encode(jobId);
    }

    /**
     * 最终报文的判断。优先看响应头；头被丢掉时，再认正文里的 status=DONE。
     */
    private boolean finished(SimulateHttpClient.SimulateHttpExchange exchange) {
        if (exchange.done()) {
            return true;
        }
        if (StrUtil.isNotBlank(exchange.phase())) {
            return false;
        }
        if (StrUtil.isBlank(exchange.body()) || !JSONUtil.isTypeJSONObject(exchange.body())) {
            return false;
        }
        return "DONE".equalsIgnoreCase(JSONUtil.parseObj(exchange.body()).getStr("status"));
    }

    /**
     * 从响应头或受理报文里取作业号
     */
    private String jobIdOf(SimulateHttpClient.SimulateHttpExchange exchange) {
        if (StrUtil.isNotBlank(exchange.jobId())) {
            return exchange.jobId();
        }
        if (StrUtil.isBlank(exchange.body()) || !JSONUtil.isTypeJSONObject(exchange.body())) {
            return null;
        }
        JSONObject object = JSONUtil.parseObj(exchange.body());
        Object jobId = object.get("jobId");
        return jobId == null ? null : String.valueOf(jobId);
    }

    /**
     * 两次轮询之间的等待
     */
    private boolean sleep(DeviceInfoDO device, int intervalMs) {
        try {
            Thread.sleep(Math.max(intervalMs, 0));
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("[poll][设备({}) 轮询等待被中断]", device.getDeviceCode());
            return false;
        }
    }

}
