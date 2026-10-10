package cn.boheng.frame.module.flow.service.definition;

import cn.boheng.frame.module.device.api.dto.DeviceSummaryDTO;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.boheng.frame.module.flow.service.definition.model.FlowMapping;
import cn.boheng.frame.module.flow.service.definition.model.FlowNodeDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 直线流程的结构、设备和发布指纹。不启动 Spring。
 */
class FlowGraphCheckerTest {

    /**
     * 两步直线、上一步取值、设备和动作都在时，发布检查应为空。
     */
    @Test
    void publishAcceptsLinearDeviceSteps() {
        List<FlowIssue> issues = FlowGraphChecker.checkPublish(List.of(step("spin", "离心", "DEV-1", "SPIN"),
                step("read", "读板", "DEV-2", "READ_PLATE", mapping("od", "spin", "od"))), lookup(true));
        assertTrue(issues.isEmpty());
    }

    /**
     * 同一步参数不能重复，也不能越过上一步去取值。
     */
    @Test
    void rejectsDuplicateParamAndSkippedPredecessor() {
        FlowNodeDraft third = step("archive", "归档", "DEV-1", "SPIN", mapping("od", "spin", "od"));
        List<FlowIssue> issues = FlowGraphChecker.checkStructure(List.of(
                step("spin", "离心", "DEV-1", "SPIN"),
                step("read", "读板", "DEV-2", "READ_PLATE", mapping("a", "spin", "x"), mapping("a", "spin", "y")),
                third));
        assertTrue(issues.stream().anyMatch(issue -> issue.getMessage().contains("重复")));
        assertTrue(issues.stream().anyMatch(issue -> issue.getMessage().contains("只能取上一步")));
    }

    /**
     * 设备不存在、动作停用、一步都没有，都要在发布时说清楚。
     */
    @Test
    void publishNamesMissingDeviceAndDisabledAction() {
        List<FlowIssue> empty = FlowGraphChecker.checkPublish(List.of(), lookup(true));
        assertEquals("至少添加一步设备动作", empty.get(0).getMessage());

        List<FlowIssue> missing = FlowGraphChecker.checkPublish(
                List.of(step("spin", "离心", "GONE", "SPIN")), lookup(false));
        assertTrue(missing.get(0).getMessage().contains("GONE"));

        List<FlowIssue> disabled = FlowGraphChecker.checkPublish(
                List.of(step("spin", "离心", "DEV-1", "SPIN")), new FlowGraphChecker.DeviceLookup() {
                    @Override
                    public DeviceSummaryDTO getByCode(String deviceCode) {
                        DeviceSummaryDTO device = new DeviceSummaryDTO();
                        device.setDeviceCode(deviceCode);
                        device.setDeviceName("离心机");
                        device.setDeviceTypeCode("CENTRIFUGE");
                        return device;
                    }

                    @Override
                    public boolean hasEnabledAction(String deviceTypeCode, String actionCode) {
                        return false;
                    }
                });
        assertTrue(disabled.stream().anyMatch(issue -> issue.getMessage().contains("没有启用的动作")));
    }

    /**
     * 同一张图的指纹稳定。改动作后指纹必须变。
     */
    @Test
    void checksumChangesWhenActionChanges() {
        List<FlowNodeDraft> nodes = List.of(step("spin", "离心", "DEV-1", "SPIN"));
        String first = FlowGraphChecker.checksum(nodes);
        assertEquals(first, FlowGraphChecker.checksum(nodes));
        nodes.get(0).setActionCode("STOP");
        assertTrue(!first.equals(FlowGraphChecker.checksum(nodes)));
    }

    /**
     * 造一步设备动作。mapping 为空表示没有额外参数。
     */
    private static FlowNodeDraft step(String nodeId, String name, String deviceCode, String actionCode, FlowMapping... mappings) {
        FlowNodeDraft draft = new FlowNodeDraft();
        draft.setNodeId(nodeId);
        draft.setName(name);
        draft.setStepType(FlowConstants.STEP_DEVICE_COMMAND);
        draft.setDeviceCode(deviceCode);
        draft.setActionCode(actionCode);
        draft.setTimeoutMs(FlowConstants.DEFAULT_TIMEOUT_MS);
        draft.setMaxAttempts(1);
        draft.setFailStrategy(FlowConstants.FAIL_FAST);
        if (mappings != null && mappings.length > 0) {
            draft.setMappings(new java.util.ArrayList<>(List.of(mappings)));
        }
        return draft;
    }

    /**
     * 从上一步取一个字段。
     */
    private static FlowMapping mapping(String param, String nodeId, String field) {
        FlowMapping mapping = new FlowMapping();
        mapping.setParam(param);
        mapping.setSource(FlowConstants.SOURCE_NODE);
        mapping.setNodeId(nodeId);
        mapping.setField(field);
        return mapping;
    }

    /**
     * 测试用设备。found 为 false 时编码一律找不到。
     */
    private static FlowGraphChecker.DeviceLookup lookup(boolean found) {
        return new FlowGraphChecker.DeviceLookup() {
            @Override
            public DeviceSummaryDTO getByCode(String deviceCode) {
                if (!found || "GONE".equals(deviceCode)) {
                    return null;
                }
                DeviceSummaryDTO device = new DeviceSummaryDTO();
                device.setDeviceCode(deviceCode);
                device.setDeviceName("测试设备");
                device.setDeviceTypeCode("CENTRIFUGE");
                return device;
            }

            @Override
            public boolean hasEnabledAction(String deviceTypeCode, String actionCode) {
                return found;
            }
        };
    }

}
