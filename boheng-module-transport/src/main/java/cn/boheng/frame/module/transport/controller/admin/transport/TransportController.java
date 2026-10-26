package cn.boheng.frame.module.transport.controller.admin.transport;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.module.transport.api.dto.TransportRequestCreateReqDTO;
import cn.boheng.frame.module.transport.api.dto.TransportRequestRespDTO;
import cn.boheng.frame.module.transport.service.TransportRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * Transport 运输入口。
 *
 * <p>Flow、WMS、人工操作和外部系统都通过此入口提交运输需求；
 * 设备命令仍由 Device 模块负责。</p>
 */
@Tag(name = "管理后台 - Transport 运输")
@RestController
@RequestMapping("/transport")
@Validated
public class TransportController {

    /** 运输需求服务。 */
    @Resource
    private TransportRequestService transportRequestService;

    /** 创建运输需求。 */
    @PostMapping("/request/create")
    @Operation(summary = "创建运输需求")
    public CommonResult<TransportRequestRespDTO> createRequest(
            @Valid @RequestBody TransportRequestCreateReqDTO request) {
        return success(transportRequestService.createRequest(request));
    }
}
