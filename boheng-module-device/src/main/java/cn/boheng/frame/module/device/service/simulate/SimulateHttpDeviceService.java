package cn.boheng.frame.module.device.service.simulate;

import cn.boheng.frame.module.device.dal.dataobject.simulate.DeviceSimulateProfileDO;
import cn.boheng.frame.module.device.dal.mysql.device.DeviceInfoMapper;
import cn.boheng.frame.module.device.dal.mysql.simulate.DeviceSimulateProfileMapper;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.enums.SimulateReturnModeEnum;
import cn.boheng.frame.module.device.service.callback.DeviceCallbackService;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 仿真设备侧的 HTTP 行为。平台通过真实 HTTP 调到这里，这里按配置决定立刻回、稍后回调，还是先受理等轮询。
 */
@Service
@Slf4j
public class SimulateHttpDeviceService {

    /**
     * 轮询就绪等待的上限
     */
    static final int MAX_WAIT_MS = 120_000;

    /**
     * 回调延迟的上限，和平台等回调的上限一致
     */
    static final int MAX_CALLBACK_DELAY_MS = DeviceCallbackService.MAX_TIMEOUT_SEC * 1000;

    /**
     * 仿真设备回调平台的 HTTP 超时
     */
    private static final int CALLBACK_TIMEOUT_MS = 20_000;

    /**
     * 没有配置最终报文时的默认结果
     */
    private static final String DEFAULT_DONE = "{\"status\":\"DONE\"}";

    /**
     * 没有配置受理报文时的默认结果
     */
    private static final String DEFAULT_ACK = "{\"status\":\"ACCEPTED\"}";

    /**
     * 没有配置进行中报文时的默认结果
     */
    private static final String DEFAULT_PENDING = "{\"status\":\"RUNNING\"}";

    /**
     * 回调线程编号
     */
    private static final AtomicInteger THREAD_NO = new AtomicInteger();

    /**
     * 到点发回调的定时线程。守护线程，不拦应用退出
     */
    private final ScheduledExecutorService callbackScheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "simulate-callback-" + THREAD_NO.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 设备台账
     */
    @Resource
    private DeviceInfoMapper deviceInfoMapper;

    /**
     * 仿真配置
     */
    @Resource
    private DeviceSimulateProfileMapper profileMapper;

    /**
     * 轮询作业
     */
    @Resource
    private SimulateJobStore jobStore;

    /**
     * 处理一次下发。SYNC 立即返回，CALLBACK 先受理再定时回调，POLL 只受理。
     */
    public SimulateDeviceReply invoke(String deviceCode, String actionCode, String requestBody,
                                      String commandNo, String callbackUrl, String callbackToken) {
        DeviceSimulateProfileDO profile = enabledProfile(deviceCode, actionCode);
        if (profile == null) {
            return failed("仿真配置不存在或未启用");
        }
        SimulateReturnModeEnum mode = SimulateReturnModeEnum.of(profile.getReturnMode());
        if (mode == null) {
            return failed("回收方式无法识别");
        }
        if (mode == SimulateReturnModeEnum.POLL) {
            return accept(profile, requestBody, Math.min(waitMs(profile), MAX_WAIT_MS));
        }
        if (mode == SimulateReturnModeEnum.CALLBACK) {
            return acceptForCallback(profile, requestBody, commandNo, callbackUrl, callbackToken);
        }
        return done(SimulateMessageRenderer.renderReply(
                StrUtil.blankToDefault(profile.getResponseBody(), DEFAULT_DONE), requestBody, null));
    }

    /**
     * 查询一次轮询作业。未到就绪时间返回进行中报文。设备编码对不上视为不存在。
     */
    public SimulateDeviceReply poll(String deviceCode, String jobId) {
        SimulateJobStore.SimulateJob job = jobStore.get(jobId);
        if (job == null || !StrUtil.equals(job.deviceCode(), deviceCode)) {
            return failed("轮询作业不存在或已过期");
        }
        if (System.currentTimeMillis() < job.readyAtMillis()) {
            return new SimulateDeviceReply(200, SimulateDeviceReply.PENDING, job.id(), job.pendingBody(), job.pendingType());
        }
        return new SimulateDeviceReply(200, SimulateDeviceReply.DONE, job.id(), job.responseBody(), job.responseType());
    }

    /**
     * 应用关闭时停掉还没发出的回调
     */
    @PreDestroy
    public void shutdown() {
        callbackScheduler.shutdownNow();
    }

    /**
     * 受理轮询作业，结果在等待时间到达后才可取走
     */
    private SimulateDeviceReply accept(DeviceSimulateProfileDO profile, String requestBody, int waitMs) {
        String jobId = IdUtil.getSnowflakeNextIdStr();
        String pending = SimulateMessageRenderer.renderReply(
                StrUtil.blankToDefault(profile.getPendingBody(), DEFAULT_PENDING), requestBody, jobId);
        String done = SimulateMessageRenderer.renderReply(
                StrUtil.blankToDefault(profile.getResponseBody(), DEFAULT_DONE), requestBody, jobId);
        String ack = SimulateMessageRenderer.renderReply(
                StrUtil.blankToDefault(profile.getAckBody(), DEFAULT_ACK), requestBody, jobId);
        long readyAt = System.currentTimeMillis() + waitMs;
        jobStore.put(new SimulateJobStore.SimulateJob(jobId, profile.getDeviceCode(), readyAt, pending, done,
                SimulateMessageRenderer.mediaType(pending), SimulateMessageRenderer.mediaType(done)));
        return new SimulateDeviceReply(200, SimulateDeviceReply.ACCEPTED, jobId, ack, SimulateMessageRenderer.mediaType(ack));
    }

    /**
     * 先回受理报文，等待时间到了再把响应报文 POST 到平台给的回调地址
     */
    private SimulateDeviceReply acceptForCallback(DeviceSimulateProfileDO profile, String requestBody,
                                                  String commandNo, String callbackUrl, String callbackToken) {
        if (StrUtil.isBlank(callbackUrl)) {
            return failed("请求里没有回调地址 " + DeviceCallbackService.CALLBACK_URL_HEADER);
        }
        Map<String, Object> fields = SimulateMessageRenderer.fieldsOf(requestBody);
        if (StrUtil.isNotBlank(commandNo)) {
            fields.putIfAbsent("commandNo", commandNo);
        }
        String result = StrUtil.blankToDefault(SimulateMessageRenderer.render(
                StrUtil.blankToDefault(profile.getResponseBody(), DEFAULT_DONE), fields), "");
        String ack = StrUtil.blankToDefault(SimulateMessageRenderer.render(
                StrUtil.blankToDefault(profile.getAckBody(), DEFAULT_ACK), fields), "");
        int delayMs = Math.min(waitMs(profile), MAX_CALLBACK_DELAY_MS);
        String deviceCode = profile.getDeviceCode();
        callbackScheduler.schedule(() -> sendCallback(deviceCode, callbackUrl, commandNo, callbackToken, result),
                delayMs, TimeUnit.MILLISECONDS);
        return new SimulateDeviceReply(200, SimulateDeviceReply.ACCEPTED, null, ack, SimulateMessageRenderer.mediaType(ack));
    }

    /**
     * 像真设备一样回调平台。只记日志，回调失败由平台按超时处理。
     */
    private void sendCallback(String deviceCode, String callbackUrl, String commandNo, String callbackToken, String body) {
        try {
            HttpRequest request = HttpRequest.post(callbackUrl).timeout(CALLBACK_TIMEOUT_MS)
                    .body(body).contentType(SimulateMessageRenderer.mediaType(body));
            if (StrUtil.isNotBlank(commandNo)) {
                request.header(DeviceCallbackService.COMMAND_HEADER, commandNo);
            }
            if (StrUtil.isNotBlank(callbackToken)) {
                request.header(DeviceCallbackService.TOKEN_HEADER, callbackToken);
            }
            try (HttpResponse response = request.execute()) {
                if (response.getStatus() >= 300) {
                    log.warn("[sendCallback][仿真设备({}) 回调 {} 被拒：HTTP {} {}]", deviceCode, callbackUrl,
                            response.getStatus(), response.body());
                } else {
                    log.info("[sendCallback][仿真设备({}) 命令({}) 已回调]", deviceCode, commandNo);
                }
            }
        } catch (Exception ex) {
            log.warn("[sendCallback][仿真设备({}) 回调 {} 失败]", deviceCode, callbackUrl, ex);
        }
    }

    /**
     * 最终报文
     */
    private SimulateDeviceReply done(String body) {
        return new SimulateDeviceReply(200, SimulateDeviceReply.DONE, null, body, SimulateMessageRenderer.mediaType(body));
    }

    /**
     * 设备侧拒绝，调用方按 HTTP 失败处理
     */
    private SimulateDeviceReply failed(String message) {
        String body = "{\"status\":\"FAILED\",\"message\":\"" + message + "\"}";
        return new SimulateDeviceReply(404, SimulateDeviceReply.FAILED, null, body, "application/json;charset=UTF-8");
    }

    /**
     * 按设备编码和动作找到启用的仿真配置
     */
    private DeviceSimulateProfileDO enabledProfile(String deviceCode, String actionCode) {
        DeviceInfoDO device = deviceInfoMapper.selectByDeviceCode(deviceCode);
        if (device == null || StrUtil.isBlank(actionCode)) {
            return null;
        }
        DeviceSimulateProfileDO profile = profileMapper.selectByDeviceAndAction(device.getId(), actionCode);
        if (profile == null || (profile.getStatus() != null && profile.getStatus() != 0)) {
            return null;
        }
        return profile;
    }

    /**
     * 配置中的等待毫秒，空或负数当 0
     */
    private int waitMs(DeviceSimulateProfileDO profile) {
        int wait = profile.getAsyncWaitMs() == null ? 0 : profile.getAsyncWaitMs();
        return Math.max(wait, 0);
    }

}
