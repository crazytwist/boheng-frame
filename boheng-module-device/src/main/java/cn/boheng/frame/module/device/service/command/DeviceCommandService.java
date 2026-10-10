package cn.boheng.frame.module.device.service.command;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandPageReqVO;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;

import java.time.LocalDateTime;

/**
 * 命令记录服务。测试调用从这里落库
 */
public interface DeviceCommandService {

    /**
     * 调用开始时落一条已占用的命令。来源按人工测试记录
     */
    DeviceCommandDO open(DeviceInfoDO device, DeviceActionDO action, String paramsJson, String codecCode);

    /**
     * 调用开始时落一条已占用的命令，带来源标记。
     * 流程引擎下发的命令用这个入口，溯源字段（来源 / 引用 / 第几次尝试）由调用方给出
     */
    DeviceCommandDO open(DeviceInfoDO device, DeviceActionDO action, String paramsJson, String codecCode,
                         CommandSource source);

    /**
     * 命令来源。人工测试入口不传，按 MANUAL 记
     */
    record CommandSource(String sourceType, String sourceRef, Integer attempt, String operator) {

        /**
         * 人工测试的缺省来源
         */
        public static CommandSource manual() {
            return new CommandSource("MANUAL", null, 1, null);
        }

    }

    /**
     * 写入请求、响应和原始数据；配了解析规则时再生成测量
     */
    void complete(Long commandId, DeviceInvokeRespVO resp, String codecCode);

    /**
     * 调用没有完成时把命令标成失败
     */
    void fail(Long commandId, String message);

    /**
     * 按这次仿真调用记下发方式和结果回收方式
     */
    void bindMode(Long commandId, String dispatchMode, String resultMode);

    /**
     * 记下已轮询次数。还没拿到结果时命令停在等待轮询。
     */
    void recordPoll(Long commandId, int pollCount, boolean done);

    /**
     * 发请求之前把命令转入等回调，记下令牌摘要和截止时刻
     */
    void awaitCallback(Long commandId, String tokenHash, LocalDateTime timeoutAt);

    /**
     * 请求发出后记下请求报文和设备的受理应答
     */
    void recordSent(Long commandId, DeviceInvokeRespVO ack);

    /**
     * 收下设备回调的结果。命令已经不在等回调时返回 false，不做任何改动。
     */
    boolean completeCallback(Long commandId, String body, String contentType);

    /**
     * 等回调到期，命令还在等就标成超时
     */
    void timeout(Long commandId, String message);

    /**
     * 只取命令当前状态，等回调时反复查用
     */
    String getStatus(Long commandId);

    /**
     * 命令记录分页
     */
    PageResult<DeviceCommandRespVO> getCommandPage(DeviceCommandPageReqVO pageReqVO);

    /**
     * 命令详情，带上原始响应正文
     */
    DeviceCommandRespVO getCommand(Long id);

}
