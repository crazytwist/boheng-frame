package cn.boheng.frame.module.flow.controller.admin.execution;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.flow.controller.admin.execution.vo.FlowDeviceTaskPageReqVO;
import cn.boheng.frame.module.flow.controller.app.task.vo.FlowDeviceTaskRespVO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskDO;
import cn.boheng.frame.module.flow.dal.mysql.execution.FlowDeviceTaskMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 设备任务监控（管理后台）。查看队列里待领取 / 执行中 / 已完成的任务，
 * 复用运行查询权限 flow:execution:query。
 */
@Tag(name = "管理后台 - 设备任务")
@RestController
@RequestMapping("/flow/device-task")
@Validated
public class FlowDeviceTaskController {

    /**
     * 任务表
     */
    @Resource
    private FlowDeviceTaskMapper deviceTaskMapper;

    /**
     * 任务分页。
     */
    @GetMapping("/page")
    @Operation(summary = "获得设备任务分页")
    @PreAuthorize("@ss.hasPermission('flow:execution:query')")
    public CommonResult<PageResult<FlowDeviceTaskRespVO>> getDeviceTaskPage(@Valid FlowDeviceTaskPageReqVO reqVO) {
        PageResult<FlowDeviceTaskDO> page = deviceTaskMapper.selectPage(reqVO);
        return success(new PageResult<>(
                page.getList().stream().map(row -> BeanUtils.toBean(row, FlowDeviceTaskRespVO.class)).toList(),
                page.getTotal()));
    }

}
