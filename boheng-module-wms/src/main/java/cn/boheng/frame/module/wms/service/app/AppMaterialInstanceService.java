package cn.boheng.frame.module.wms.service.app;

import cn.boheng.frame.module.wms.controller.app.vo.MaterialIdQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialItem;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialLiquidTypeQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialPosQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialPositionAreaQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialPositionAreaRespVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialStatusTypeQueryReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.MaterialUpdateCmdReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.TipConsumeReqVO;
import cn.boheng.frame.module.wms.controller.app.vo.TipResourceRespVO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 机器端 / 外部兼容 — 物料实例查询与状态更新
 */
public interface AppMaterialInstanceService {

    List<MaterialItem> listByPos(@Valid MaterialPosQueryReqVO query);

    List<MaterialItem> listByLiquidAndType(@Valid MaterialLiquidTypeQueryReqVO query);

    List<MaterialItem> listByStatusAndType(@Valid MaterialStatusTypeQueryReqVO query);

    List<MaterialItem> listByIds(@Valid MaterialIdQueryReqVO query);

    Boolean updateLabware(@Valid List<MaterialUpdateCmdReqVO> updateCmdList);

    MaterialPositionAreaRespVO queryPositionByArea(@Valid MaterialPositionAreaQueryReqVO areaQuery);

    Boolean tipConsume(@Valid TipConsumeReqVO cmd);

    TipResourceRespVO queryTipResource(String areaCode);

    Boolean tipComplement(String tipId);
}
