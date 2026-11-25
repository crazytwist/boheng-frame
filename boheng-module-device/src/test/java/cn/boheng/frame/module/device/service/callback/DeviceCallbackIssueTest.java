package cn.boheng.frame.module.device.service.callback;

import cn.boheng.frame.framework.web.config.WebProperties;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 签发回调地址的单元测试。
 *
 * <p>盯的是路径前缀：设备回调入口在 {@code controller.open} 包下，路径原样是
 * {@code /api/callback/...}，不带 {@code /admin-api}。一旦有人把默认模板改回
 * 管理端前缀，设备侧就拿不到结果，且只有真机联调才会暴露——太重，所以在这里挡住。</p>
 */
class DeviceCallbackIssueTest {

    /**
     * 远端根地址，模拟真设备能访问到的平台地址
     */
    private static final String REMOTE_BASE = "http://10.0.0.5:48080";

    /**
     * 被测服务
     */
    private DeviceCallbackService service;

    /**
     * 环境，用来放地址、端口、上下文路径
     */
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        DevicePlatformUrl platformUrl = new DevicePlatformUrl();
        ReflectionTestUtils.setField(platformUrl, "environment", environment);
        ReflectionTestUtils.setField(platformUrl, "webProperties", new WebProperties());

        service = new DeviceCallbackService();
        ReflectionTestUtils.setField(service, "platformUrl", platformUrl);
    }

    /**
     * 配了根地址时，默认回调地址落在 /api/callback 下，而不是 /admin-api
     */
    @Test
    void shouldIssueCallbackUrlWithoutAdminPrefix() {
        environment.setProperty(DevicePlatformUrl.CALLBACK_BASE_PROPERTY, REMOTE_BASE);

        String url = service.issue(device(null), null, "C0001", false).url();

        assertTrue(url.startsWith(REMOTE_BASE + "/api/callback/"), url);
        assertTrue(url.contains("/C0001?"), url);
        assertTrue(url.contains("token="), url);
        assertFalse(url.contains("/admin-api"), url);
    }

    /**
     * 没配根地址时退回本机，仍然不带管理端前缀
     */
    @Test
    void shouldFallBackToLocalhostWithOpenApiPath() {
        String url = service.issue(device(null), null, "C0002", false).url();

        assertTrue(url.startsWith("http://127.0.0.1:48080/api/callback/C0002?"), url);
        assertFalse(url.contains("/admin-api"), url);
    }

    /**
     * 仿真设备在同一个进程里，即便配了远端地址也用本机回环
     */
    @Test
    void shouldUseLoopbackForSimulatedDevice() {
        environment.setProperty(DevicePlatformUrl.CALLBACK_BASE_PROPERTY, REMOTE_BASE);
        environment.setProperty("local.server.port", "48199");

        String url = service.issue(device(null), null, "C0003", true).url();

        assertTrue(url.startsWith("http://127.0.0.1:48199/api/callback/C0003?"), url);
    }

    /**
     * 有上下文路径时要带上，但只带上下文路径，不能把 admin 前缀一起拼进来
     */
    @Test
    void shouldKeepContextPathButNotAdminPrefix() {
        environment.setProperty(DevicePlatformUrl.CALLBACK_BASE_PROPERTY, REMOTE_BASE);
        environment.setProperty("server.servlet.context-path", "/boheng/");

        String url = service.issue(device(null), null, "C0004", false).url();

        assertTrue(url.startsWith(REMOTE_BASE + "/boheng/api/callback/C0004?"), url);
    }

    /**
     * 台账里自己配了回调地址时以台账为准，三个占位符都要替换掉
     */
    @Test
    void shouldHonourDeviceCallbackUrlTemplate() {
        DeviceInfoDO device = device("http://vendor.local/hook/${commandNo}"
                + "?t=${callbackToken}&d=${deviceCode}");

        DeviceCallbackTicket ticket = service.issue(device, null, "C0005", false);

        assertTrue(ticket.url().startsWith("http://vendor.local/hook/C0005?t="), ticket.url());
        assertTrue(ticket.url().endsWith("&d=DEV-01"), ticket.url());
        assertTrue(ticket.url().contains(ticket.token()), ticket.url());
    }

    /**
     * 超时时间：动作上的优先，其次台账，都没有用默认值
     */
    @Test
    void shouldPreferActionTimeoutOverDevice() {
        DeviceInfoDO device = device(null);
        device.setCallbackTimeoutSec(120);
        DeviceActionDO action = new DeviceActionDO();
        action.setCallbackTimeoutSec(30);

        assertEquals(30, service.timeoutSec(device, action));
        assertEquals(120, service.timeoutSec(device, new DeviceActionDO()));
    }

    /**
     * 配 0 表示不超时：截止时刻留空。
     *
     * <p>留空是关键——超时扫描器按 {@code timeout_at < now} 收口，
     * 留空才不会被它当成「超时」，也才不会被 stale 兜底规则打死。</p>
     */
    @Test
    void shouldNotSetDeadlineWhenTimeoutIsZero() {
        DeviceActionDO action = new DeviceActionDO();
        action.setCallbackTimeoutSec(0);

        DeviceCallbackTicket ticket = service.issue(device(null), action, "C0006", false);

        assertEquals(0, ticket.timeoutSec());
        assertNull(ticket.timeoutAt());
        assertFalse(ticket.url().isEmpty());
    }

    /**
     * 台账配 0、动作没配时同样不超时（0 不能被当成「没配」回落到默认 300）
     */
    @Test
    void shouldNotFallBackToDefaultWhenDeviceSaysZero() {
        DeviceInfoDO device = device(null);
        device.setCallbackTimeoutSec(0);

        DeviceCallbackTicket ticket = service.issue(device, new DeviceActionDO(), "C0007", false);

        assertEquals(0, ticket.timeoutSec());
        assertNull(ticket.timeoutAt());
    }

    /**
     * 正数仍然收在上限内，负数按不超时处理（配置写错时不至于让命令秒超时）
     */
    @Test
    void shouldCapPositiveTimeoutAndTreatNegativeAsNoTimeout() {
        DeviceActionDO tooLong = new DeviceActionDO();
        tooLong.setCallbackTimeoutSec(99999);
        assertEquals(DeviceCallbackService.MAX_TIMEOUT_SEC, service.timeoutSec(null, tooLong));

        DeviceActionDO negative = new DeviceActionDO();
        negative.setCallbackTimeoutSec(-1);
        assertEquals(DeviceCallbackService.NO_TIMEOUT_SEC, service.timeoutSec(null, negative));
    }

    /**
     * 一台设备的最小台账，只需要编码和可选的回调地址模板
     */
    private static DeviceInfoDO device(String callbackUrl) {
        DeviceInfoDO device = new DeviceInfoDO();
        device.setDeviceCode("DEV-01");
        device.setCallbackUrl(callbackUrl);
        return device;
    }

}
