package cn.boheng.frame.module.wms.controller.admin.container;

import cn.boheng.frame.framework.common.pojo.CommonResult;
import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.wms.controller.admin.container.vo.ContainerTypePageReqVO;
import cn.boheng.frame.module.wms.controller.admin.container.vo.ContainerTypeRespVO;
import cn.boheng.frame.module.wms.controller.admin.container.vo.ContainerTypeSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.container.ContainerTypeDO;
import cn.boheng.frame.module.wms.service.container.ContainerTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;

import static cn.boheng.frame.framework.common.pojo.CommonResult.success;

/**
 * 容器类型 Controller
 *
 * @author yinan
 */
@Tag(name = "管理后台 - 容器类型")
@RestController
@RequestMapping("/wms/container-type")
@Validated
public class ContainerTypeController {

    @Resource
    private ContainerTypeService containerTypeService;


    @PostMapping("/create")
    @Operation(summary = "创建容器类型")
    @PreAuthorize("@ss.hasPermission('wms:container-type:create')")
    public CommonResult<Long> createContainerType(@Valid @RequestBody ContainerTypeSaveReqVO createReqVO) {
        return success(containerTypeService.createContainerType(createReqVO));
    }


    @PutMapping("/update")
    @Operation(summary = "更新容器类型")
    @PreAuthorize("@ss.hasPermission('wms:container-type:update')")
    public CommonResult<Boolean> updateContainerType(@Valid @RequestBody ContainerTypeSaveReqVO updateReqVO) {
        containerTypeService.updateContainerType(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除容器类型")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:container-type:delete')")
    public CommonResult<Boolean> deleteContainerType(@RequestParam("id") Long id) {
        containerTypeService.deleteContainerType(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Operation(summary = "批量删除容器类型")
    @Parameter(name = "ids", description = "编号列表", required = true)
    @PreAuthorize("@ss.hasPermission('wms:container-type:delete')")
    public CommonResult<Boolean> deleteContainerTypeList(@RequestParam("ids") List<Long> ids) {
        containerTypeService.deleteContainerTypeList(ids);
        return success(true);
    }


    @GetMapping("/page")
    @Operation(summary = "获得容器类型分页")
    @PreAuthorize("@ss.hasPermission('wms:container-type:query')")
    public CommonResult<PageResult<ContainerTypeRespVO>> getContainerTypePage(@Valid ContainerTypePageReqVO pageReqVO) {
        PageResult<ContainerTypeDO> pageResult = containerTypeService.getContainerTypePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, ContainerTypeRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得容器类型")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('wms:container-type:query')")
    public CommonResult<ContainerTypeRespVO> getContainerType(@RequestParam("id") Long id) {
        ContainerTypeDO containerType = containerTypeService.getContainerType(id);
        return success(BeanUtils.toBean(containerType, ContainerTypeRespVO.class));
    }


    @GetMapping("/list")
    @Operation(summary = "获得容器类型列表")
    @PreAuthorize("@ss.hasPermission('wms:container-type:query')")
    public CommonResult<List<ContainerTypeRespVO>> getContainerTypeList() {
        List<ContainerTypeDO> list = containerTypeService.getContainerTypeList();
        return success(BeanUtils.toBean(list, ContainerTypeRespVO.class));
    }
}
