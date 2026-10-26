package cn.boheng.frame.module.transport.service;

import cn.boheng.frame.module.transport.api.dto.TransportRequestCreateReqDTO;
import cn.boheng.frame.module.transport.api.dto.TransportRequestRespDTO;
import cn.boheng.frame.module.transport.dal.dataobject.TransportRequestDO;
import cn.boheng.frame.module.transport.dal.mysql.TransportRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Transport 运输需求服务实现。 */
@Service
@RequiredArgsConstructor
public class TransportRequestServiceImpl implements TransportRequestService {

    /** 运输需求 Mapper。 */
    private final TransportRequestMapper requestMapper;

    /** 创建初始状态为 CREATED 的运输需求。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TransportRequestRespDTO createRequest(TransportRequestCreateReqDTO request) {
        String requestNo = "TR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        TransportRequestDO data = TransportRequestDO.builder()
                .requestNo(requestNo).requestType("MATERIAL_TRANSFER")
                .sourceType(request.getSourceType()).sourceRef(request.getSourceRef())
                .priority(request.getPriority() == null ? 100 : request.getPriority())
                .status("CREATED").idempotencyKey(request.getIdempotencyKey()).build();
        requestMapper.insert(data);
        TransportRequestRespDTO response = new TransportRequestRespDTO();
        response.setRequestId(data.getId());
        response.setRequestNo(data.getRequestNo());
        response.setStatus(data.getStatus());
        return response;
    }
}
