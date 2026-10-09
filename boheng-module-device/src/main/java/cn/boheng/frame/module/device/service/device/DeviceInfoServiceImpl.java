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
import cn.boheng.frame.module.device.dal.mysql.action.DeviceActionMapper;
import cn.boheng.frame.module.device.dal.mysql.device.DeviceInfoMapper;
import cn.boheng.frame.module.device.dal.mysql.paramset.DeviceParamSetMapper;
import cn.boheng.frame.module.device.dal.mysql.property.DevicePropertyMapper;
import cn.boheng.frame.module.device.service.http.DeviceLoginTokenCache;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.annotations.VisibleForTesting;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.*;

/**
 * 设备台账 Service 实现类
 *
 * @author yinan
 */
@Service
@Validated
public class DeviceInfoServiceImpl implements DeviceInfoService {

    /** 占用锁时间在画像里的展示格式 */
    private static final DateTimeFormatter LOCK_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
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
     * 创建设备台账
     */

    @Override
    public Long createDevice(DeviceSaveReqVO createReqVO) {
        // 校验设备编码唯一性
        validateDeviceCodeUnique(createReqVO.getId(), createReqVO.getDeviceCode());

        // 插入
        DeviceInfoDO device = BeanUtils.toBean(createReqVO, DeviceInfoDO.class);
        deviceInfoMapper.insert(device);
        return device.getId();
    }
    /**
     * 更新设备台账。登录密码留空时保留原密码
     */

    @Override
    public void updateDevice(DeviceSaveReqVO updateReqVO) {
        // 校验存在
        validateDeviceExists(updateReqVO.getId());
        // 校验设备编码唯一性
        validateDeviceCodeUnique(updateReqVO.getId(), updateReqVO.getDeviceCode());

        // 更新。密码留空表示不改，避免把已保存的登录密码清掉。
        DeviceInfoDO updateObj = BeanUtils.toBean(updateReqVO, DeviceInfoDO.class);
        if (StrUtil.isBlank(updateReqVO.getLoginPassword())) {
            updateObj.setLoginPassword(null);
        }
        deviceInfoMapper.updateById(updateObj);
        deviceLoginTokenCache.evict(updateReqVO.getId());
    }
    /**
     * 删除设备台账，并清掉缓存的登录令牌
     */

    @Override
    public void deleteDevice(Long id) {
        // 校验存在
        validateDeviceExists(id);
        // 删除
        deviceInfoMapper.deleteById(id);
        deviceLoginTokenCache.evict(id);
    }
    /**
     * 批量删除设备台账
     */

    @Override
    public void deleteDeviceList(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        deviceInfoMapper.deleteBatch(DeviceInfoDO::getId, ids);
        ids.forEach(deviceLoginTokenCache::evict);
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
        String type = device.getConnectionType() == null ? "" : device.getConnectionType();
        String body = switch (type) {
            case "HTTP" -> "平台通过 HTTP 直接访问" + blank(device.getEndpointUrl(), "尚未填写的地址") + "。";
            case "MQTT" -> "设备主动连到 MQTT，主题前缀是 " + blank(device.getMqttTopicPrefix(), "尚未填写") + "。";
            case "NODE_RED" -> "私有协议先由 Node-RED 翻译，平台只接收翻译后的 HTTP。";
            case "SIMULATED" -> "接入方式本身就是仿真。";
            default -> "还没有选定接入方式。";
        };
        String result = Boolean.TRUE.equals(device.getCallbackEnabled())
                ? "仪器可以主动把结果推回来。"
                : "仪器不主动推结果，平台按 " + (device.getPollIntervalSec() == null ? "默认间隔" : device.getPollIntervalSec() + " 秒") + "轮询。";
        if ("SIMULATED".equals(type)) {
            return mode + body;
        }
        return mode + body + result;
    }

    /**
     * 画像里的下发策略说明
     */
    private String buildDispatchSummary(DeviceInfoDO device) {
        String concurrency = device.getConcurrencyPolicy() == null ? "EXCLUSIVE" : device.getConcurrencyPolicy();
        String busy = device.getBusyCheckPolicy() == null ? "AUTO" : device.getBusyCheckPolicy();
        int inflight = device.getMaxInflight() == null ? 1 : device.getMaxInflight();
        return switch (concurrency) {
            case "DEVICE_QUEUED" -> "仪器自己带队列，平台可以连续下发，同时在途不超过 " + inflight + " 条。"
                    + busyClause(busy);
            case "PLATFORM_QUEUED" -> "仪器不接受并发，由平台排队，一次只发出一条。" + busyClause(busy);
            default -> "同一时间只占用这一台设备。" + busyClause(busy);
        };
    }

    /**
     * 忙闲校验开关对应的说明
     */
    private String busyClause(String busy) {
        return switch (busy) {
            case "ALWAYS" -> "下发前一律先问仪器是否空闲。";
            case "NEVER" -> "不向仪器询问是否空闲，避免不报状态的设备永远下不了命令。";
            default -> "下发前查不查空闲，看每个动作自己的设置。";
        };
    }

    /**
     * 画像里的占用锁说明
     */
    private String buildOccupancySummary(DeviceInfoDO device) {
        String concurrency = device.getConcurrencyPolicy() == null ? "EXCLUSIVE" : device.getConcurrencyPolicy();
        if (!"EXCLUSIVE".equals(concurrency)) {
            int current = device.getInflightCount() == null ? 0 : device.getInflightCount();
            int max = device.getMaxInflight() == null ? 1 : device.getMaxInflight();
            return "当前在途 " + current + " 条，上限 " + max + " 条。";
        }
        boolean held = StrUtil.isNotBlank(device.getLockHolder()) || device.getCurrentCommandId() != null;
        if (!held) {
            return "现在没有人占用。";
        }
        LocalDateTime expire = device.getLockExpireTime();
        if (expire != null && expire.isBefore(LocalDateTime.now())) {
            return "锁已经在 " + expire.format(LOCK_TIME) + " 过期，可以重新占用。";
        }
        String holder = blank(device.getLockHolder(), "未记录持有方");
        String until = expire == null ? "没有填写过期时间" : "到 " + expire.format(LOCK_TIME);
        return holder + " 占用中，" + until + "。";
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
