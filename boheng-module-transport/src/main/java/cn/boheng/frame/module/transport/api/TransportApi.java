package cn.boheng.frame.module.transport.api;

import cn.boheng.frame.module.transport.api.dto.TransportRequestCreateReqDTO;
import cn.boheng.frame.module.transport.api.dto.TransportRequestRespDTO;

/**
 * Transport 运输域对外契约。
 *
 * <p>Flow、WMS 和人工入口只依赖该契约，不直接访问 Transport 的实现层。</p>
 */
public interface TransportApi {

    /**
     * 提交运输需求。
     *
     * @param request 运输需求
     * @return 创建后的运输需求摘要
     */
    TransportRequestRespDTO createRequest(TransportRequestCreateReqDTO request);
}
