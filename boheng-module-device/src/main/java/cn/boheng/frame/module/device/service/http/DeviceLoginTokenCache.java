package cn.boheng.frame.module.device.service.http;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备登录 token 的进程内缓存。到期或登录信息变更后丢弃，下次调用重新登录。
 */
@Component
public class DeviceLoginTokenCache {

    private final ConcurrentHashMap<Long, Entry> tokens = new ConcurrentHashMap<>();

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

    public void put(Long deviceId, String token, int ttlSec) {
        if (deviceId == null || token == null) {
            return;
        }
        long ttlMillis = Math.max(ttlSec, 1) * 1000L;
        tokens.put(deviceId, new Entry(token, System.currentTimeMillis() + ttlMillis));
    }

    public void evict(Long deviceId) {
        if (deviceId != null) {
            tokens.remove(deviceId);
        }
    }

    private record Entry(String token, long expireAtMillis) {
    }

}
