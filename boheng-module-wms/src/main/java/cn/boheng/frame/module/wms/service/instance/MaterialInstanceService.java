package cn.boheng.frame.module.wms.service.instance;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeByContainerReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceConsumeReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstancePageReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstancePutInReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceSaveReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceTakeOutReqVO;
import cn.boheng.frame.module.wms.controller.admin.instance.vo.MaterialInstanceTransferReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.instance.MaterialInstanceDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 物料实例 Service 接口
 *
 * ⚠️ 落位相关操作（上架 / 下架 / 转移）**必须**走本接口的专用方法，不允许通过
 * {@link #updateMaterialInstance} 直接改 rootSlotId —— 否则会绕过物料流水，
 * 导致「位置变了但没记录」。更新接口已显式拒绝改动槽位字段。
 *
 * @author yinan
 */
public interface MaterialInstanceService {


    /**
     * 创建物料实例
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createMaterialInstance(@Valid MaterialInstanceSaveReqVO createReqVO);


    /**
     * 批量创建物料实例（同一父实例下批量生成子实例，如孔板一次性生成全部孔位）
     *
     * @param createReqVOs 创建信息列表
     * @return 编号列表
     */
    List<Long> createMaterialInstanceBatch(@Valid List<MaterialInstanceSaveReqVO> createReqVOs);


    /**
     * 更新物料实例
     *
     * <p>⚠️ 本方法**不接受**改动落位槽位。若传入的 rootSlotId 与当前值不同会直接报错，
     * 请改用 {@link #putIn} / {@link #takeOut} / {@link #transfer}。</p>
     *
     * @param updateReqVO 更新信息
     */
    void updateMaterialInstance(@Valid MaterialInstanceSaveReqVO updateReqVO);

    /**
     * 删除物料实例
     *
     * @param id 编号
     */
    void deleteMaterialInstance(Long id);

    /**
     * 批量删除物料实例
     *
     * @param ids 编号列表
     */
    void deleteMaterialInstanceList(List<Long> ids);


    // ==================== 落位操作（上架 / 下架 / 转移） ====================

    /**
     * 上架：把顶层实例放到目标槽位（未落位 → 落位；已在别的槽位 → 请用转移）
     *
     * @param reqVO 上架信息
     * @return 流水编号
     */
    Long putIn(@Valid MaterialInstancePutInReqVO reqVO);

    /**
     * 下架：把在架实例从槽位取下（root_slot_id 置空）
     *
     * @param reqVO 下架信息
     * @return 流水编号
     */
    Long takeOut(@Valid MaterialInstanceTakeOutReqVO reqVO);

    /**
     * 转移：把在架实例从当前槽位挪到目标槽位（一条 MOVE 流水，不拆成下架+上架）
     *
     * @param reqVO 转移信息
     * @return 流水编号
     */
    Long transfer(@Valid MaterialInstanceTransferReqVO reqVO);


    // ==================== 消耗 ====================

    /**
     * 精确消耗：items 中每一项都是被消耗的那个实例本身（可以是根实例也可以是子实例），
     * 支持离散计数（qty）与连续体积（volUl）。每个实例记一条 CONSUME 流水。
     *
     * @param reqVO 消耗信息
     * @return 流水编号列表（顺序与 items 一致）
     */
    List<Long> consume(@Valid MaterialInstanceConsumeReqVO reqVO);

    /**
     * 按容器消耗：调用方只知道「要从这个容器拿 N 个」，由后端按策略挑选可用子实例后逐个消耗。
     *
     * @param reqVO 按容器消耗信息
     * @return 实际被消耗掉的子实例编号列表
     */
    List<Long> consumeByContainer(@Valid MaterialInstanceConsumeByContainerReqVO reqVO);


    // ==================== 查询 ====================

    /**
     * 获得物料实例
     *
     * @param id 编号
     * @return 物料实例
     */
    MaterialInstanceDO getMaterialInstance(Long id);

    /**
     * 获得物料实例分页
     *
     * @param pageReqVO 分页查询
     * @return 物料实例分页
     */
    PageResult<MaterialInstanceDO> getMaterialInstancePage(MaterialInstancePageReqVO pageReqVO);


    /**
     * 获得物料实例列表
     *
     * @return 物料实例列表
     */
    List<MaterialInstanceDO> getMaterialInstanceList();

    /**
     * 获得未落位的顶层实例列表（供「上架」弹窗选择源）
     *
     * @return 未落位的顶层实例
     */
    List<MaterialInstanceDO> getUnplacedList();
}
