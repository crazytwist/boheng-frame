package cn.boheng.frame.module.device.service.simulate;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 轮询作业的内存登记。仿真设备受理后把结果放在这里，等服务端来取。
 */
@Component
public class SimulateJobStore {

    /**
     * 结果就绪后仍保留的时间，避免最后一次轮询刚好错过
     */
    private static final long KEEP_AFTER_READY_MS = 10 * 60 * 1000L;

    /**
     * 作业号到作业的映射
     */
    private final ConcurrentHashMap<String, SimulateJob> jobs = new ConcurrentHashMap<>();

    /**
     * 登记一个轮询作业
     */
    public void put(SimulateJob job) {
        sweep();
        jobs.put(job.id(), job);
    }

    /**
     * 按作业号取出。过期的视为不存在。
     */
    public SimulateJob get(String jobId) {
        sweep();
        SimulateJob job = jobs.get(jobId);
        if (job == null || job.expireAtMillis() <= System.currentTimeMillis()) {
            jobs.remove(jobId);
            return null;
        }
        return job;
    }

    /**
     * 清掉已经过保留期的作业
     */
    private void sweep() {
        long now = System.currentTimeMillis();
        jobs.entrySet().removeIf(entry -> entry.getValue().expireAtMillis() <= now);
    }

    /**
     * 一次已受理、尚未被取走的仿真结果。
     *
     * @param id 作业号
     * @param deviceCode 受理这单的设备编码，轮询时用来对上设备
     * @param readyAtMillis 可以返回最终报文的时间
     * @param pendingBody 未就绪时的报文
     * @param responseBody 就绪后的报文
     * @param pendingType 未就绪报文的类型
     * @param responseType 最终报文的类型
     */
    public record SimulateJob(String id, String deviceCode, long readyAtMillis, String pendingBody, String responseBody,
                              String pendingType, String responseType) {

        /**
         * 超过这个时间的作业不再接受轮询
         */
        public long expireAtMillis() {
            return readyAtMillis + KEEP_AFTER_READY_MS;
        }

    }

}
