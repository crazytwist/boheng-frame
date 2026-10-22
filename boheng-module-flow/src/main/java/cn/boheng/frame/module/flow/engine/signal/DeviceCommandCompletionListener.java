package cn.boheng.frame.module.flow.engine.signal;

import cn.boheng.frame.module.device.api.dto.DeviceCommandCompletedEvent;
import jakarta.annotation.Resource;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 接收设备域统一完成事件，驱动等待中的流程节点立即结算。 */
@Component
public class DeviceCommandCompletionListener {

    @Resource
    private FlowSignalDispatcher signalDispatcher;

    @EventListener
    public void onCompleted(DeviceCommandCompletedEvent event) {
        signalDispatcher.deviceCommandSignal(event.commandId());
    }
}
