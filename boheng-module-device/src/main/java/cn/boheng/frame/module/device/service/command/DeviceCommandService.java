package cn.boheng.frame.module.device.service.command;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandCloseReqVO;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandPageReqVO;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;

import java.time.LocalDateTime;

import static cn.boheng.frame.module.device.enums.DeviceEnums.CommandSourceTypeEnum;

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
    default DeviceCommandDO open(DeviceInfoDO device, DeviceActionDO action, String paramsJson, String codecCode,
                                 CommandSource source) {
        return open(device, action, paramsJson, codecCode, source, null);
    }

    /**
     * 落命令，并记录每个参数的取值来源。
     *
     * @param paramsSource 参数来源 JSON：{参数名: INPUT 流程传入 / SCHEMA_DEFAULT 契约默认}
     */
    DeviceCommandDO open(DeviceInfoDO device, DeviceActionDO action, String paramsJson, String codecCode,
                         CommandSource source, String paramsSource);

    /**
     * 命令来源。人工测试入口不传，按 MANUAL 记
     */
    record CommandSource(String sourceType, String sourceRef, Integer attempt, String operator) {

        /**
         * 人工测试的缺省来源
         */
        public static CommandSource manual() {
            return new CommandSource(CommandSourceTypeEnum.MANUAL.getType(), null, 1, null);
        }

    }

    /**
     * 写入请求、响应和原始数据；配了解析规则时再生成测量
     *
     * @return 这次应答的判定结论，供调用方回填响应对象
     */
    cn.boheng.frame.module.device.service.result.ResultVerdict complete(Long commandId,
                                                                      DeviceInvokeRespVO resp, String codecCode);

    /**
     * 调用没有完成时把命令标成失败
     */
    void fail(Long commandId, String message);

    /**
     * 按判定结论把命令标成失败。
     *
     * <p>用于「受理应答里设备已经明确拒绝」这类情况：HTTP 可能是 200，但业务码说明它不接这单，
     * 命令不该继续挂在等回调的状态里。失败分类和可重试性原样带上，流程侧据此决定要不要重试。</p>
     *
     * @param commandId 命令编号
     * @param verdict 设备域给出的判定结论
     */
    void fail(Long commandId, cn.boheng.frame.module.device.service.result.ResultVerdict verdict);

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
    void markWaitingCallback(Long commandId, String tokenHash, LocalDateTime timeoutAt);

    /**
     * 请求发出后记下请求报文和设备的受理应答
     */
    void recordSent(Long commandId, DeviceInvokeRespVO ack);

    /**
     * 记下这条命令的固定地址回调关联键值。
     *
     * <p>厂商把回调地址写死、报文里又给不了命令号时，回调只能靠这个值反查命令。
     * 值取自实际发出的报文里 {@code device_action.correlate_path} 指向的字段，
     * 所以必须在渲染之后调用。取不到就什么都不写，不影响正常回调路径。</p>
     *
     * @param commandId 命令编号
     * @param correlateValue 关联键值；为空时忽略
     */
    void bindCorrelateValue(Long commandId, String correlateValue);

    /**
     * 收下设备回调的结果。命令已经不在等回调时返回 false，不做任何改动。
     */
    boolean completeCallback(Long commandId, String body, String contentType);

    /**
     * 等回调到期，命令还在等就标成超时
     */
    void timeout(Long commandId, String message);

    /**
     * 人工给一条卡住的命令定结论。
     *
     * <p>只用于非终态命令（回调丢失、设备实际已完成等）。和超时收口走同一条路：
     * 改状态 → 发完成事件 → 流程侧等待中的节点立即结算。区别是结论由人给，
     * 且可重试性必须显式声明。</p>
     *
     * @param reqVO 目标状态、是否可重试、失败分类与原因
     * @return 结束后的命令
     */
    DeviceCommandRespVO manualClose(DeviceCommandCloseReqVO reqVO);

    /**
     * 只取命令当前状态，等回调时反复查用
     */
    String getStatus(Long commandId);

    /**
     * 读取命令原始响应，并按命令绑定的解析规则返回结构化 JSON。
     */
    String getParsedOutput(Long commandId);

    /**
     * 命令记录分页
     */
    PageResult<DeviceCommandRespVO> getCommandPage(DeviceCommandPageReqVO pageReqVO);

    /**
     * 命令详情，带上原始响应正文
     */
    DeviceCommandRespVO getCommand(Long id);

}
