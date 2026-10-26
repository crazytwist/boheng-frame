package cn.boheng.frame.module.transport.service;

import cn.boheng.frame.module.transport.api.dto.TransportRequestCreateReqDTO;
import cn.boheng.frame.module.transport.api.dto.TransportRequestRespDTO;

/** Transport 运输需求服务。 */
public interface TransportRequestService {

    /** 创建运输需求。 */
    TransportRequestRespDTO createRequest(TransportRequestCreateReqDTO request);
}
