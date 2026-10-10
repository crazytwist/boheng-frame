package cn.boheng.frame.module.device.service.callback;

import cn.boheng.frame.module.device.service.command.DeviceCommandService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_CALLBACK_INTERRUPTED;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_CALLBACK_TIMEOUT;

/**
 * 让发起调用的请求停在这里等回调。
 *
 * 回调打到本机时立即唤醒；打到别的实例时，靠每秒查一次命令状态发现。
 */
@Component
public class DeviceCallbackWaiter {

    /**
     * 两次查库之间最长等多久
     */
    private static final long CHECK_INTERVAL_MS = 1_000;

    /**
     * 正在等回调的命令
     */
    private final Map<Long, Slot> slots = new ConcurrentHashMap<>();

    /**
     * 命令记录，用来查状态和标超时
     */
    @Resource
    private DeviceCommandService deviceCommandService;

    /**
     * 等到命令离开等回调状态。到期仍在等就标成超时并抛出。
     */
    public void await(Long commandId, String deviceCode, DeviceCallbackTicket ticket) {
        Slot slot = slots.computeIfAbsent(commandId, id -> new Slot());
        long deadline = System.currentTimeMillis() + ticket.timeoutSec() * 1000L;
        try {
            while (true) {
                if (!"WAITING".equals(deviceCommandService.getStatus(commandId))) {
                    return;
                }
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    break;
                }
                slot.sleep(Math.min(left, CHECK_INTERVAL_MS));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw exception(DEVICE_CALLBACK_INTERRUPTED, deviceCode);
        } finally {
            slots.remove(commandId);
        }
        deviceCommandService.timeout(commandId, "等待 " + ticket.timeoutSec() + " 秒仍未收到设备回调");
        if (!"TIMED_OUT".equals(deviceCommandService.getStatus(commandId))) {
            return;
        }
        throw exception(DEVICE_CALLBACK_TIMEOUT, deviceCode, ticket.timeoutSec());
    }

    /**
     * 回调已经收下，叫醒在等这条命令的请求
     */
    public void signal(Long commandId) {
        Slot slot = slots.get(commandId);
        if (slot != null) {
            slot.wake();
        }
    }

    /**
     * 一条命令的等待位
     */
    private static final class Slot {

        /**
         * 等待用的锁
         */
        private final ReentrantLock lock = new ReentrantLock();

        /**
         * 回调到达的信号
         */
        private final Condition arrived = lock.newCondition();

        /**
         * 是否已经被叫醒过，防止信号先于等待到达时丢失
         */
        private boolean woken;

        /**
         * 最多睡指定毫秒，被叫醒就提前返回
         */
        void sleep(long millis) throws InterruptedException {
            lock.lock();
            try {
                if (!woken) {
                    arrived.await(millis, TimeUnit.MILLISECONDS);
                }
                woken = false;
            } finally {
                lock.unlock();
            }
        }

        /**
         * 叫醒等待方
         */
        void wake() {
            lock.lock();
            try {
                woken = true;
                arrived.signalAll();
            } finally {
                lock.unlock();
            }
        }

    }

}
