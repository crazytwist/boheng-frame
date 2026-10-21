package cn.boheng.frame.module.flow.service.task;

import cn.boheng.frame.module.flow.controller.app.task.vo.ExternalTaskDomainRespVO;
import cn.boheng.frame.module.flow.dal.dataobject.execution.FlowDeviceTaskDO;
import cn.boheng.frame.module.wms.api.MaterialInstanceExternalApi;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * 清华 {@code TaskParamInterceptor} 对齐：对部分任务类型补全 config。
 */
@Component
public class FlowDeviceTaskParamEnhancer {

    private static final String LIQUID_TRANSFECTION = "LT_Transfection_Liquid";
    private static final String LIQUID_293T_VIRUS = "LT_293T_Virus";

    @Resource
    private MaterialInstanceExternalApi materialInstanceExternalApi;

    /**
     * 按 actionCode 增强已组装的任务域对象。
     */
    public void enhance(FlowDeviceTaskDO task, ExternalTaskDomainRespVO domain) {
        if (task == null || domain == null || domain.getBatchId() == null) {
            return;
        }
        String batchKey = String.valueOf(domain.getBatchId());
        String actionCode = task.getActionCode();
        if ("ST_Cell_Transfection".equals(actionCode)) {
            injectTransfectionTubeId(domain, batchKey, LIQUID_TRANSFECTION);
        } else if ("ST_JurkatCellTransfection".equals(actionCode)) {
            injectTransfectionTubeId(domain, batchKey, LIQUID_293T_VIRUS);
        }
    }

    private void injectTransfectionTubeId(ExternalTaskDomainRespVO domain, String batchKey, String liquidTypeName) {
        Integer tubeId = materialInstanceExternalApi.findFirstEncodedLabwareId(batchKey, liquidTypeName);
        if (tubeId == null) {
            return;
        }
        JSONObject entries = domain.getConfig() == null
                ? new JSONObject()
                : JSONUtil.parseObj(domain.getConfig());
        entries.set("TransfectionTubeID", tubeId);
        domain.setConfig(entries);
    }

}
