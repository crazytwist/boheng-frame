package cn.boheng.frame.module.flow.controller.admin.error;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.flow.controller.admin.error.vo.FlowErrorActionRespVO;
import cn.boheng.frame.module.flow.controller.admin.error.vo.FlowErrorRecordPageReqVO;
import cn.boheng.frame.module.flow.controller.admin.error.vo.FlowErrorRecordRespVO;
import cn.boheng.frame.module.flow.dal.dataobject.error.FlowErrorActionDO;
import cn.boheng.frame.module.flow.dal.dataobject.error.FlowErrorRecordDO;
import cn.boheng.frame.module.flow.service.error.FlowErrorRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 流程错误记录只读查询（doc/07 E-2；人工处置属 E-5，本阶段不做）。
 */
@Tag(name = "管理后台 - 流程错误记录")
@RestController
@RequestMapping("/flow/error-record")
@Validated
public class FlowErrorRecordController {

    @Resource
    private FlowErrorRecordService errorRecordService;

    @GetMapping("/get")
    @Operation(summary = "获得错误记录详情（含处置流水）")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('flow:execution:query')")
    public CommonResult<FlowErrorRecordRespVO> getError(@RequestParam("id") Long id) {
        FlowErrorRecordDO record = errorRecordService.getError(id);
        if (record == null) {
            return success(null);
        }
        FlowErrorRecordRespVO vo = BeanUtils.toBean(record, FlowErrorRecordRespVO.class);
        List<FlowErrorActionDO> actions = errorRecordService.getActions(id);
        vo.setActions(actions == null ? Collections.emptyList()
                : actions.stream().map(a -> BeanUtils.toBean(a, FlowErrorActionRespVO.class)).toList());
        return success(vo);
    }

    @GetMapping("/page")
    @Operation(summary = "获得错误记录分页")
    @PreAuthorize("@ss.hasPermission('flow:execution:query')")
    public CommonResult<PageResult<FlowErrorRecordRespVO>> getErrorPage(@Valid FlowErrorRecordPageReqVO pageReqVO) {
        PageResult<FlowErrorRecordDO> page = errorRecordService.getErrorPage(pageReqVO);
        return success(new PageResult<>(
                page.getList().stream().map(row -> BeanUtils.toBean(row, FlowErrorRecordRespVO.class)).toList(),
                page.getTotal()));
    }
}
