package cn.boheng.frame.module.flow.service.task;

import cn.boheng.frame.module.flow.controller.app.task.vo.ExternalTaskDomainRespVO;

/**
 * 将 {@code flow_device_task} 组装为清华 {@code ExternalTaskDomain} 结构。
 */
public interface FlowDeviceTaskExternalService {

    /**
     * 按区域（device_code）取第一条 PENDING 任务并组装；无任务返回 {@code null}。
     */
    ExternalTaskDomainRespVO listFirstExecutableByArea(String area);

}
