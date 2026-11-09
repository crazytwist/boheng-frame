package cn.boheng.frame.module.device.api;

import cn.boheng.frame.module.device.api.dto.DeviceCommandSendReqDTO;
import cn.boheng.frame.module.device.api.dto.DeviceCommandResultDTO;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeReqVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.mysql.command.DeviceCommandMapper;
import cn.boheng.frame.module.device.dal.mysql.device.DeviceInfoMapper;
import cn.boheng.frame.module.device.service.command.DeviceCommandService;
import cn.boheng.frame.module.device.service.http.DeviceHttpService;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import static cn.boheng.frame.module.device.enums.DeviceEnums.CommandSourceTypeEnum;
import static cn.boheng.frame.module.device.enums.DeviceEnums.CommandStatusEnum;

/**
 * 设备命令契约实现。给流程引擎用的下发入口：
 * 复用 {@link DeviceHttpService} 的「校验 → 占用 → 落命令 → 下发」流程，
 * 把所有异常收成失败 DTO，不跨模块抛业务异常。
 *
 * <p>溯源：请求里的 sourceType / sourceRef / attempt 落到命令表；
 * 空白 sourceType 记为 MANUAL，与按来源查询使用同一套归一化。
 * 「哪个运行哪个节点哪次尝试发的哪条命令」事后沿 source_ref 可查。</p>
 */
@Service
@Validated
public class DeviceCommandApiImpl implements DeviceCommandApi {

    /**
     * 设备台账表。按编码找设备主键
     */
    @Resource
    private DeviceInfoMapper deviceInfoMapper;

    /**
     * 设备调用全流程
     */
    @Resource
    private DeviceHttpService deviceHttpService;

    /**
     * 命令查询服务
     */
    @Resource
    private DeviceCommandService deviceCommandService;

    /**
     * 命令表查询；来源键在当前租户内唯一。
     */
    @Resource
    private DeviceCommandMapper deviceCommandMapper;

    /**
     * 下发命令。相同来源键重复提交时回放原命令的当前结果。
     */
    @Override
    public DeviceCommandResultDTO send(DeviceCommandSendReqDTO req) {
        DeviceCommandResultDTO original = getResultBySource(req.getSourceType(), req.getSourceRef());
        if (original != null) {
            return original;
        }
        DeviceCommandResultDTO result = new DeviceCommandResultDTO();
        DeviceInfoDO device = StrUtil.isBlank(req.getDeviceCode())
                ? null : deviceInfoMapper.selectByDeviceCode(req.getDeviceCode().trim());
        if (device == null) {
            result.setSuccess(false);
            result.setErrorMessage("设备不存在: " + req.getDeviceCode());
            return result;
        }
        DeviceInvokeReqVO invokeReq = new DeviceInvokeReqVO();
        invokeReq.setDeviceId(device.getId());
        invokeReq.setActionCode(req.getActionCode());
        invokeReq.setParamsJson(req.getParamsJson());
        // 溯源来源透传：FLOW 场景 sourceRef 形如 flow:{executionId}:{nodeId}:{attempt}
        DeviceCommandService.CommandSource source = new DeviceCommandService.CommandSource(
                normalizeSourceType(req.getSourceType()),
                normalizeSourceRef(req.getSourceRef()), req.getAttempt(), null);
        try {
            DeviceInvokeRespVO resp = deviceHttpService.invoke(invokeReq, source);
            boolean ok = resp.getStatus() != null && resp.getStatus() >= 200 && resp.getStatus() < 300;
            result.setCommandId(resp.getCommandId());
            result.setCommandNo(resp.getCommandNo());
            result.setSuccess(ok);
            result.setStatus(StrUtil.blankToDefault(resp.getCommandStatus(),
                    ok ? CommandStatusEnum.SUCCEEDED.getStatus() : CommandStatusEnum.FAILED.getStatus()));
            result.setOutputJson(resp.getBody());
            if (!ok) {
                result.setErrorMessage("设备返回 HTTP " + resp.getStatus());
            }
            return result;
        } catch (RuntimeException ex) {
            // 并发重复提交可能在命令插入时撞唯一键，也可能在抢占设备时遇到原命令。
            original = getResultBySource(req.getSourceType(), req.getSourceRef());
            if (original != null) {
                return original;
            }
            // 设备域的业务异常（忙/停用/超时等）在这里收口，Flow 只看结果
            result.setSuccess(false);
            result.setStatus(CommandStatusEnum.FAILED.getStatus());
            result.setErrorMessage(StrUtil.maxLength(StrUtil.nullToEmpty(ex.getMessage()), 512));
            return result;
        }
    }

    /**
     * 查询命令当前状态及终态结果
     */
    @Override
    public DeviceCommandResultDTO getResult(Long commandId) {
        DeviceCommandResultDTO result = new DeviceCommandResultDTO();
        result.setCommandId(commandId);
        try {
            DeviceCommandRespVO command = deviceCommandService.getCommand(commandId);
            result.setCommandNo(command.getCommandNo());
            result.setStatus(command.getStatus());
            result.setSuccess(CommandStatusEnum.SUCCEEDED.getStatus().equals(command.getStatus()));
            result.setOutputJson(command.getRawBody());
            result.setParsedOutputJson(deviceCommandService.getParsedOutput(commandId));
            result.setErrorMessage(command.getErrorMsg());
        } catch (RuntimeException ex) {
            result.setStatus(CommandStatusEnum.FAILED.getStatus());
            result.setSuccess(false);
            result.setErrorMessage(StrUtil.maxLength(StrUtil.nullToEmpty(ex.getMessage()), 512));
        }
        return result;
    }

    /**
     * 查询同一租户的来源命令；来源引用为空时没有可回放的业务键。
     * 来源类型与落库使用同一套归一化，空白按 MANUAL 处理。
     */
    @Override
    public DeviceCommandResultDTO getResultBySource(String sourceType, String sourceRef) {
        String normalizedRef = normalizeSourceRef(sourceRef);
        if (normalizedRef == null) {
            return null;
        }
        DeviceCommandDO command = deviceCommandMapper.selectBySource(normalizeSourceType(sourceType), normalizedRef);
        return command == null ? null : getResult(command.getId());
    }

    /**
     * 与落库一致的来源类型：去掉首尾空格，空白记为 MANUAL。
     */
    private static String normalizeSourceType(String sourceType) {
        return StrUtil.blankToDefault(StrUtil.trimToNull(sourceType), CommandSourceTypeEnum.MANUAL.getType());
    }

    /**
     * 与落库一致的来源引用：去掉首尾空格，空白视为没有可回放的业务键。
     */
    private static String normalizeSourceRef(String sourceRef) {
        return StrUtil.trimToNull(sourceRef);
    }

}
