package cn.boheng.frame.module.flow.service.definition;

import cn.boheng.frame.module.device.api.dto.DeviceSummaryDTO;
import cn.boheng.frame.module.flow.enums.FlowConstants;
import cn.boheng.frame.module.flow.service.definition.model.FlowIssue;
import cn.boheng.frame.module.flow.service.definition.model.FlowMapping;
import cn.boheng.frame.module.flow.service.definition.model.FlowNodeDraft;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 检查一条直线流程能不能保存、能不能发布，并计算发布指纹。
 * 不访问数据库。设备是否存在由调用方用 {@link DeviceLookup} 回答。
 */
public final class FlowGraphChecker {

    /**
     * 步骤编码：小写开头，方便出现在日志和回调路径里。
     */
    private static final Pattern NODE_ID = Pattern.compile(FlowConstants.NODE_ID_REGEX);

    /**
     * 参数名和字段名。允许大写，因为设备动作的参数常常是 READ_PLATE 这种风格。
     */
    private static final Pattern FIELD_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,63}$");

    private FlowGraphChecker() {
    }

    /**
     * 保存草稿时就要成立的结构。设备可以先空着，人还在选。
     */
    public static List<FlowIssue> checkStructure(List<FlowNodeDraft> nodes) {
        List<FlowIssue> issues = new ArrayList<>();
        if (nodes == null || nodes.isEmpty()) {
            return issues;
        }
        if (nodes.size() > FlowConstants.MAX_NODES) {
            issues.add(new FlowIssue(null, "一条流程最多 " + FlowConstants.MAX_NODES + " 步"));
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < nodes.size(); i++) {
            FlowNodeDraft node = nodes.get(i);
            String label = labelOf(node, i);
            checkIdentity(issues, seen, node, label);
            checkLimits(issues, node, label);
            checkMappings(issues, nodes, i, label);
        }
        return issues;
    }

    /**
     * 发布时在结构之外再确认：至少一步，每一步都指向一台还在、动作仍启用的设备。
     */
    public static List<FlowIssue> checkPublish(List<FlowNodeDraft> nodes, DeviceLookup lookup) {
        List<FlowIssue> issues = new ArrayList<>(checkStructure(nodes));
        if (nodes == null || nodes.isEmpty()) {
            issues.add(new FlowIssue(null, "至少添加一步设备动作"));
            return issues;
        }
        for (int i = 0; i < nodes.size(); i++) {
            FlowNodeDraft node = nodes.get(i);
            String label = labelOf(node, i);
            if (StrUtil.hasBlank(node.getDeviceCode(), node.getActionCode())) {
                issues.add(new FlowIssue(node.getNodeId(), "第 " + (i + 1) + " 步「" + label + "」还没选择设备和动作"));
                continue;
            }
            DeviceSummaryDTO device = lookup.getByCode(node.getDeviceCode());
            if (device == null) {
                issues.add(new FlowIssue(node.getNodeId(), "设备【" + node.getDeviceCode() + "】不存在"));
                continue;
            }
            if (!lookup.hasEnabledAction(device.getDeviceTypeCode(), node.getActionCode())) {
                issues.add(new FlowIssue(node.getNodeId(),
                        "设备【" + device.getDeviceName() + "】没有启用的动作【" + node.getActionCode() + "】"));
            }
        }
        return issues;
    }

    /**
     * 按步骤顺序和映射内容计算指纹。同一张图每次发布得到同一个值。
     */
    public static String checksum(List<FlowNodeDraft> nodes) {
        StringBuilder text = new StringBuilder();
        for (FlowNodeDraft node : nodes) {
            text.append(node.getNodeId()).append('|')
                    .append(node.getDeviceCode()).append('|')
                    .append(node.getActionCode()).append('|')
                    .append(node.getTimeoutMs()).append('|')
                    .append(node.getMaxAttempts()).append('|')
                    .append(node.getFailStrategy()).append('|');
            List<FlowMapping> mappings = new ArrayList<>(node.getMappings() == null ? List.of() : node.getMappings());
            mappings.sort(Comparator.comparing(FlowMapping::getParam, Comparator.nullsFirst(String::compareTo)));
            for (FlowMapping mapping : mappings) {
                text.append(mapping.getParam()).append('=')
                        .append(mapping.getSource()).append(':')
                        .append(StrUtil.nullToEmpty(mapping.getNodeId())).append(':')
                        .append(StrUtil.nullToEmpty(mapping.getField())).append(':')
                        .append(StrUtil.nullToEmpty(mapping.getValue())).append(',');
            }
            text.append('\n');
        }
        return DigestUtil.sha256Hex(text.toString());
    }

    /**
     * 发布失败时把多条原因收成一句，塞进错误码的占位符。
     */
    public static String join(List<FlowIssue> issues) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < issues.size(); i++) {
            if (i > 0) {
                text.append('；');
            }
            text.append(issues.get(i).getMessage());
        }
        return text.toString();
    }

    /**
     * 编码、名称、类型。编码在整条流程里不能重复。
     */
    private static void checkIdentity(List<FlowIssue> issues, Set<String> seen, FlowNodeDraft node, String label) {
        if (StrUtil.isBlank(node.getNodeId()) || !NODE_ID.matcher(node.getNodeId()).matches()) {
            issues.add(new FlowIssue(node.getNodeId(), "步骤「" + label + "」的编码须是小写字母开头，只含小写、数字和下划线"));
        } else if (!seen.add(node.getNodeId())) {
            issues.add(new FlowIssue(node.getNodeId(), "步骤编码【" + node.getNodeId() + "】重复"));
        }
        if (StrUtil.isBlank(node.getName()) || node.getName().length() > 128) {
            issues.add(new FlowIssue(node.getNodeId(), "步骤「" + label + "」的名称不能为空，且不超过 128 字"));
        }
        if (!FlowConstants.STEP_DEVICE_COMMAND.equals(node.getStepType())) {
            issues.add(new FlowIssue(node.getNodeId(), "当前只能添加设备动作，不能使用【" + node.getStepType() + "】"));
        }
        if (!FlowConstants.FAIL_FAST.equals(node.getFailStrategy())) {
            issues.add(new FlowIssue(node.getNodeId(), "当前失败后只能终止流程"));
        }
    }

    /**
     * 超时和次数的范围。缺省值由保存逻辑先填上，这里看到的应是具体数字。
     */
    private static void checkLimits(List<FlowIssue> issues, FlowNodeDraft node, String label) {
        Long timeout = node.getTimeoutMs();
        if (timeout == null || timeout < FlowConstants.MIN_TIMEOUT_MS || timeout > FlowConstants.MAX_TIMEOUT_MS) {
            issues.add(new FlowIssue(node.getNodeId(), "步骤「" + label + "」的等待时间须在 1 秒到 24 小时之间"));
        }
        Integer attempts = node.getMaxAttempts();
        if (attempts == null || attempts < 1 || attempts > FlowConstants.MAX_ATTEMPTS_LIMIT) {
            issues.add(new FlowIssue(node.getNodeId(), "步骤「" + label + "」的尝试次数须在 1 到 " + FlowConstants.MAX_ATTEMPTS_LIMIT + " 之间"));
        }
    }

    /**
     * 映射只能取启动入参、固定值，或紧挨着的上一步。不能跳步，也不能指向后面的步骤。
     */
    private static void checkMappings(List<FlowIssue> issues, List<FlowNodeDraft> nodes, int index, String label) {
        FlowNodeDraft node = nodes.get(index);
        if (node.getMappings() == null || node.getMappings().isEmpty()) {
            return;
        }
        String previousId = index == 0 ? null : nodes.get(index - 1).getNodeId();
        Set<String> params = new HashSet<>();
        for (FlowMapping mapping : node.getMappings()) {
            if (mapping == null || StrUtil.isBlank(mapping.getParam()) || !FIELD_NAME.matcher(mapping.getParam()).matches()) {
                issues.add(new FlowIssue(node.getNodeId(), "步骤「" + label + "」有一条参数名不合法"));
                continue;
            }
            if (!params.add(mapping.getParam())) {
                issues.add(new FlowIssue(node.getNodeId(), "步骤「" + label + "」的参数【" + mapping.getParam() + "】重复"));
            }
            String source = mapping.getSource();
            if (FlowConstants.SOURCE_LITERAL.equals(source)) {
                if (StrUtil.isBlank(mapping.getValue()) || mapping.getValue().length() > 512) {
                    issues.add(new FlowIssue(node.getNodeId(), "参数【" + mapping.getParam() + "】的固定值不能为空，且不超过 512 字"));
                }
            } else if (FlowConstants.SOURCE_INPUT.equals(source)) {
                if (StrUtil.isBlank(mapping.getField()) || !FIELD_NAME.matcher(mapping.getField()).matches()) {
                    issues.add(new FlowIssue(node.getNodeId(), "参数【" + mapping.getParam() + "】要写明启动时传入的字段名"));
                }
            } else if (FlowConstants.SOURCE_NODE.equals(source)) {
                if (previousId == null) {
                    issues.add(new FlowIssue(node.getNodeId(), "第一步没有上一步，参数【" + mapping.getParam() + "】不能从上一步取值"));
                } else if (!previousId.equals(mapping.getNodeId())) {
                    issues.add(new FlowIssue(node.getNodeId(), "参数【" + mapping.getParam() + "】只能取上一步【" + previousId + "】的输出"));
                }
                if (StrUtil.isBlank(mapping.getField()) || !FIELD_NAME.matcher(StrUtil.nullToEmpty(mapping.getField())).matches()) {
                    issues.add(new FlowIssue(node.getNodeId(), "参数【" + mapping.getParam() + "】要写明上一步的字段名"));
                }
            } else {
                issues.add(new FlowIssue(node.getNodeId(), "参数【" + mapping.getParam() + "】的来源无法识别"));
            }
        }
    }

    /**
     * 提示里优先用名称。名称还没填时用序号。
     */
    private static String labelOf(FlowNodeDraft node, int index) {
        if (node != null && StrUtil.isNotBlank(node.getName())) {
            return node.getName();
        }
        return "第 " + (index + 1) + " 步";
    }

    /**
     * 发布校验问设备域的两个问题。流程模块不自己查设备表。
     */
    public interface DeviceLookup {

        /**
         * 按编码取设备。没有则返回 null。
         */
        DeviceSummaryDTO getByCode(String deviceCode);

        /**
         * 该类型下动作是否仍启用。
         */
        boolean hasEnabledAction(String deviceTypeCode, String actionCode);

    }

}
