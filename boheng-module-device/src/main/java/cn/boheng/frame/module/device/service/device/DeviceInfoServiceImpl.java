package cn.boheng.frame.module.device.service.device;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.module.device.controller.admin.device.vo.DevicePageReqVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DevicePortraitRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceSaveReqVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.dataobject.paramset.DeviceParamSetDO;
import cn.boheng.frame.module.device.dal.dataobject.property.DevicePropertyDO;
import cn.boheng.frame.module.device.dal.dataobject.simulate.DeviceSimulateProfileDO;
import cn.boheng.frame.module.device.dal.mysql.action.DeviceActionMapper;
import cn.boheng.frame.module.device.dal.mysql.command.DeviceCommandMapper;
import cn.boheng.frame.module.device.dal.mysql.device.DeviceInfoMapper;
import cn.boheng.frame.module.device.dal.mysql.paramset.DeviceParamSetMapper;
import cn.boheng.frame.module.device.dal.mysql.property.DevicePropertyMapper;
import cn.boheng.frame.module.device.dal.mysql.simulate.DeviceSimulateProfileMapper;
import cn.boheng.frame.module.device.service.http.DeviceLoginTokenCache;
import cn.boheng.frame.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.DeviceEnums.ConnectionTypeEnum;
import static cn.boheng.frame.module.device.enums.DeviceEnums.TaskModeEnum;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.*;

/**
 * 设备台账 Service 实现类
 *
 * @author yinan
 */
@Service
@Validated
public class DeviceInfoServiceImpl implements DeviceInfoService {

    /**
     * 设备台账表
     */

    @Resource
    private DeviceInfoMapper deviceInfoMapper;
    /**
     * 设备动作表
     */
    @Resource
    private DeviceActionMapper deviceActionMapper;
    /**
     * 设备属性表
     */
    @Resource
    private DevicePropertyMapper devicePropertyMapper;
    /**
     * 参数集表
     */
    @Resource
    private DeviceParamSetMapper deviceParamSetMapper;
    /**
     * 设备登录令牌缓存
     */
    @Resource
    private DeviceLoginTokenCache deviceLoginTokenCache;
    /**
     * 仿真配置表，设备删除或改编码时同步
     */
    @Resource
    private DeviceSimulateProfileMapper deviceSimulateProfileMapper;
    /**
     * 命令表，删除前确认没有执行中的命令
     */
    @Resource
    private DeviceCommandMapper deviceCommandMapper;
    /**
     * 创建设备台账
     */

    @Override
    public Long createDevice(DeviceSaveReqVO createReqVO) {
        // 校验设备编码唯一性
        validateDeviceCodeUnique(createReqVO.getId(), createReqVO.getDeviceCode());
        normalizeTaskMode(createReqVO);

        // 插入
        DeviceInfoDO device = BeanUtils.toBean(createReqVO, DeviceInfoDO.class);
        deviceInfoMapper.insert(device);
        return device.getId();
    }
    /**
     * 更新设备台账。登录密码留空时保留原密码
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDevice(DeviceSaveReqVO updateReqVO) {
        // 校验存在
        DeviceInfoDO old = validateDeviceExists(updateReqVO.getId());
        // 校验设备编码唯一性
        validateDeviceCodeUnique(updateReqVO.getId(), updateReqVO.getDeviceCode());
        normalizeTaskMode(updateReqVO);

        // 更新。密码留空表示不改，避免把已保存的登录密码清掉。
        DeviceInfoDO updateObj = BeanUtils.toBean(updateReqVO, DeviceInfoDO.class);
        if (StrUtil.isBlank(updateReqVO.getLoginPassword())) {
            updateObj.setLoginPassword(null);
        }
        deviceInfoMapper.updateById(updateObj);
        if (!StrUtil.equals(old.getDeviceCode(), updateReqVO.getDeviceCode())) {
            DeviceSimulateProfileDO profile = new DeviceSimulateProfileDO();
            profile.setDeviceCode(updateReqVO.getDeviceCode());
            deviceSimulateProfileMapper.update(profile, new LambdaQueryWrapperX<DeviceSimulateProfileDO>().eq(DeviceSimulateProfileDO::getDeviceId, old.getId()));
        }
        deviceLoginTokenCache.evict(updateReqVO.getId());
    }

    /** 兼容旧设备：未填写模式时按 PUSH 处理。 */
    private void normalizeTaskMode(DeviceSaveReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getTaskMode())) {
            reqVO.setTaskMode(TaskModeEnum.PUSH.getMode());
        }
        if (TaskModeEnum.PULL.getMode().equals(reqVO.getTaskMode())) {
            // PULL 是设备主动领取任务，不支持平台侧仿真设备配置。
            reqVO.setSimulationMode(false);
        }
    }
    /**
     * 删除设备台账，连带删除它的仿真配置，并清掉缓存的登录令牌。有执行中的命令时不允许删除
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDevice(Long id) {
        DeviceInfoDO device = validateDeviceExists(id);
        validateNoRunningCommand(device);
        deviceInfoMapper.deleteById(id);
        deviceSimulateProfileMapper.delete(DeviceSimulateProfileDO::getDeviceId, id);
        deviceLoginTokenCache.evict(id);
    }
    /**
     * 批量删除设备台账
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDeviceList(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        deviceInfoMapper.selectList(DeviceInfoDO::getId, ids).forEach(this::validateNoRunningCommand);
        deviceInfoMapper.deleteBatch(DeviceInfoDO::getId, ids);
        deviceSimulateProfileMapper.deleteBatch(DeviceSimulateProfileDO::getDeviceId, ids);
        ids.forEach(deviceLoginTokenCache::evict);
    }

    /**
     * 校验设备上没有执行中的命令
     */
    private void validateNoRunningCommand(DeviceInfoDO device) {
        if (deviceCommandMapper.countRunning(device.getId()) > 0) {
            throw exception(DEVICE_DELETE_RUNNING, device.getDeviceCode());
        }
    }
    /**
     * 获得设备台账
     */

    @Override
    public DeviceInfoDO getDevice(Long id) {
        return deviceInfoMapper.selectById(id);
    }
    /**
     * 获得设备台账分页
     */

    @Override
    public PageResult<DeviceInfoDO> getDevicePage(DevicePageReqVO pageReqVO) {
        return deviceInfoMapper.selectPage(pageReqVO);
    }
    /**
     * 获得设备台账列表
     */

    @Override
    public List<DeviceInfoDO> getDeviceList() {
        return deviceInfoMapper.selectList();
    }
    /**
     * 组装设备画像：接入、动作、属性和参数集
     */

    @Override
    public DevicePortraitRespVO getDevicePortrait(Long id) {
        DeviceInfoDO device = validateDeviceExists(id);
        String typeCode = device.getDeviceTypeCode();

        DevicePortraitRespVO portrait = new DevicePortraitRespVO();
        portrait.setDevice(toResp(device));
        portrait.setConnectionSummary(buildConnectionSummary(device));
        portrait.setDispatchSummary(buildDispatchSummary(device));
        portrait.setOccupancySummary(buildOccupancySummary(device));
        portrait.setActions(toActionFacets(typeCode == null ? List.of() : deviceActionMapper.selectListByTypeCode(typeCode)));
        portrait.setProperties(toPropertyFacets(typeCode == null ? List.of() : devicePropertyMapper.selectListByTypeCode(typeCode)));
        portrait.setParamSets(toParamSetFacets(typeCode == null ? List.of() : deviceParamSetMapper.selectListByTypeCode(typeCode)));
        return portrait;
    }

    /**
     * 画像里的接入说明
     */
    private String buildConnectionSummary(DeviceInfoDO device) {
        String mode = Boolean.TRUE.equals(device.getSimulationMode()) ? "当前走仿真，不连接真机。" : "";
        ConnectionTypeEnum connectionType = ConnectionTypeEnum.fromType(device.getConnectionType());
        String body;
        if (connectionType == null) {
            body = "还没有选定接入方式。";
        } else {
            body = switch (connectionType) {
                case HTTP -> "平台通过 HTTP 直接访问" + blank(device.getEndpointUrl(), "尚未填写的地址") + "。";
                case MQTT -> "设备主动连到 MQTT，主题前缀是 " + blank(device.getMqttTopicPrefix(), "尚未填写") + "。";
                case NODE_RED -> "私有协议先由 Node-RED 翻译，平台只接收翻译后的 HTTP。";
                case SIMULATED -> "接入方式本身就是仿真。";
            };
        }
        String result = Boolean.TRUE.equals(device.getCallbackEnabled())
                ? "仪器可以主动把结果推回来。"
                : "仪器不主动推结果，平台按 " + (device.getPollIntervalSec() == null ? "默认间隔" : device.getPollIntervalSec() + " 秒") + "轮询。";
        if (connectionType == ConnectionTypeEnum.SIMULATED) {
            return mode + body;
        }
        return mode + body + result;
    }

    /** 设备域仅执行命令；容量与占用策略留给资源域。 */
    private String buildDispatchSummary(DeviceInfoDO device) {
        return "设备域直接下发命令；并发容量与占用策略待资源域实现。";
    }

    /** 画像不再将历史占用字段解释为当前可用性。 */
    private String buildOccupancySummary(DeviceInfoDO device) {
        return "设备域不判断占用状态；资源域接入前不提供占用可用性。";
    }

    /**
     * 把动作列表转成画像条目
     */
    private List<DevicePortraitRespVO.Facet> toActionFacets(List<DeviceActionDO> actions) {
        List<DevicePortraitRespVO.Facet> facets = new ArrayList<>();
        for (DeviceActionDO action : actions) {
            DevicePortraitRespVO.Facet facet = new DevicePortraitRespVO.Facet();
            facet.setTitle(blank(action.getActionName(), action.getActionCode()));
            facet.setCode(action.getActionCode());
            String duration = action.getEstimateDurationMs() == null ? "耗时未估" : formatDuration(action.getEstimateDurationMs());
            String check = Boolean.TRUE.equals(action.getNeedBusyCheck()) ? "下发前要确认空闲" : "下发前不单独确认空闲";
            facet.setDetail(duration + " · " + check);
            facet.setActive(action.getStatus() == null || action.getStatus() == 0);
            facets.add(facet);
        }
        return facets;
    }

    /**
     * 把属性列表转成画像条目
     */
    private List<DevicePortraitRespVO.Facet> toPropertyFacets(List<DevicePropertyDO> properties) {
        List<DevicePortraitRespVO.Facet> facets = new ArrayList<>();
        for (DevicePropertyDO property : properties) {
            DevicePortraitRespVO.Facet facet = new DevicePortraitRespVO.Facet();
            facet.setTitle(blank(property.getPropertyName(), property.getPropertyCode()));
            facet.setCode(property.getPropertyCode());
            String unit = StrUtil.blankToDefault(property.getUnit(), "");
            String how;
            if (Boolean.TRUE.equals(property.getSubscribable())) {
                how = "仪器可主动推送";
            } else if (property.getPollIntervalSec() != null && property.getPollIntervalSec() > 0) {
                how = "每 " + property.getPollIntervalSec() + " 秒轮询";
            } else if (Boolean.TRUE.equals(property.getReadable())) {
                how = "需要时读取";
            } else {
                how = "暂不对外读取";
            }
            facet.setDetail(StrUtil.isBlank(unit) ? how : unit + " · " + how);
            facet.setActive(property.getStatus() == null || property.getStatus() == 0);
            facets.add(facet);
        }
        return facets;
    }

    /**
     * 把参数集转成画像条目
     */
    private List<DevicePortraitRespVO.Facet> toParamSetFacets(List<DeviceParamSetDO> paramSets) {
        List<DevicePortraitRespVO.Facet> facets = new ArrayList<>();
        for (DeviceParamSetDO paramSet : paramSets) {
            DevicePortraitRespVO.Facet facet = new DevicePortraitRespVO.Facet();
            facet.setTitle(blank(paramSet.getParamSetName(), paramSet.getParamSetCode()));
            facet.setCode(paramSet.getActionCode());
            facet.setDetail(Boolean.TRUE.equals(paramSet.getValidated()) ? "已验证，可以拿去下发" : "还没验证，不能拿去下发");
            facet.setActive(paramSet.getStatus() == null || paramSet.getStatus() == 0);
            facets.add(facet);
        }
        return facets;
    }

    /**
     * 把预估毫秒数转成可读时长
     */
    private String formatDuration(Long millis) {
        if (millis < 1000) {
            return millis + " 毫秒";
        }
        if (millis % 1000 == 0) {
            return (millis / 1000) + " 秒";
        }
        return String.format("%.1f 秒", millis / 1000.0);
    }

    /**
     * 空文本换成兜底文案
     */
    private String blank(String value, String fallback) {
        return StrUtil.isBlank(value) ? fallback : value;
    }

    /**
     * 台账转响应对象，不回传登录密码
     */
    private DeviceRespVO toResp(DeviceInfoDO device) {
        DeviceRespVO resp = BeanUtils.toBean(device, DeviceRespVO.class);
        if (TaskModeEnum.PULL.getMode().equals(device.getTaskMode())) {
            resp.setSimulationMode(false);
        }
        resp.setLoginConfigured(StrUtil.isNotBlank(device.getLoginPassword()));
        resp.setLoginPassword(null);
        return resp;
    }
    /**
     * 校验设备存在
     */

    @VisibleForTesting
    DeviceInfoDO validateDeviceExists(Long id) {
        DeviceInfoDO device = deviceInfoMapper.selectById(id);
        if (device == null) {
            throw exception(DEVICE_NOT_EXISTS);
        }
        return device;
    }
    /**
     * 校验设备编码不重复
     */

    @VisibleForTesting
    void validateDeviceCodeUnique(Long id, String deviceCode) {
        if (StrUtil.isBlank(deviceCode)) {
            return;
        }
        DeviceInfoDO device = deviceInfoMapper.selectByDeviceCode(deviceCode);
        if (device == null) {
            return;
        }
        if (id == null || !device.getId().equals(id)) {
            throw exception(DEVICE_CODE_DUPLICATE, deviceCode);
        }
    }

}
