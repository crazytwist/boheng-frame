package cn.boheng.frame.module.wms.api;

import cn.boheng.frame.module.wms.api.dto.MaterialCheckReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialCheckRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeByContainerReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialConsumeRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialReleaseReqDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialUseReleaseRespDTO;
import cn.boheng.frame.module.wms.api.dto.MaterialUseReqDTO;

/**
 * 物料域对外 API（契约层，供 Flow 等模块调用）。
 *
 * <p>Phase C+D：{@code check}/{@code use}/{@code consume}/{@code consumeByContainer}/{@code release}。
 * 不含 putIn/takeOut/transfer（D3-a）；不做完整预留全家桶（D2-a）。</p>
 *
 * <p>写操作幂等（D1-a）：同一 {@code idempotentKey} 重复到达时回放首次结果。</p>
 * <p>检查（D4-a）：不通过返回结果对象，不以抛错表达业务拒绝。</p>
 */
public interface MaterialApi {

    /**
     * 上机前 / 消耗前可用性检查。整批不通过时 {@code ok=false}，明细带 reason。
     */
    MaterialCheckRespDTO check(MaterialCheckReqDTO reqDTO);

    /**
     * 最小使用：AVAILABLE 或 RESERVED → IN_USE，记 STATUS_CHANGE 流水。
     */
    MaterialUseReleaseRespDTO use(MaterialUseReqDTO reqDTO);

    /**
     * 精确消耗：按实例扣减数量/体积或整件消耗。
     */
    MaterialConsumeRespDTO consume(MaterialConsumeReqDTO reqDTO);

    /**
     * 按容器消耗：从父实例可用子实例中挑选 N 个整件消耗。
     */
    MaterialConsumeRespDTO consumeByContainer(MaterialConsumeByContainerReqDTO reqDTO);

    /**
     * 最小释放：IN_USE 或 RESERVED → AVAILABLE，记 RELEASE 流水。
     */
    MaterialUseReleaseRespDTO release(MaterialReleaseReqDTO reqDTO);

}
