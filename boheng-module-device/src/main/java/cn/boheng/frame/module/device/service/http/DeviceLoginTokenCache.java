package cn.boheng.frame.module.device.service.http;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备登录 token 的进程内缓存。到期或登录信息变更后丢弃，下次调用重新登录。
 */
@Component
public class DeviceLoginTokenCache {

    /** 设备编号到令牌的缓存 */
    private final ConcurrentHashMap<Long, Entry> tokens = new ConcurrentHashMap<>();

    /**
     * 取还没过期的令牌，过期则删掉并返回空
     */
    public String getValid(Long deviceId) {
        if (deviceId == null) {
            return null;
        }
        Entry entry = tokens.get(deviceId);
        if (entry == null) {
            return null;
        }
        if (entry.expireAtMillis <= System.currentTimeMillis()) {
            tokens.remove(deviceId, entry);
            return null;
        }
        return entry.token;
    }

    /**
     * 写入令牌，并按有效秒数计算过期时间
     */
    public void put(Long deviceId, String token, int ttlSec) {
        if (deviceId == null || token == null) {
            return;
        }
        long ttlMillis = Math.max(ttlSec, 1) * 1000L;
        tokens.put(deviceId, new Entry(token, System.currentTimeMillis() + ttlMillis));
    }

    /**
     * 丢掉这台设备缓存的登录令牌
     */
    public void evict(Long deviceId) {
        if (deviceId != null) {
            tokens.remove(deviceId);
        }
    }

    /**
     * 一条缓存的登录令牌。
     *
     * @param token 令牌
     * @param expireAtMillis 过期时间戳，毫秒
     */
    private record Entry(String token, long expireAtMillis) {
    }

}
