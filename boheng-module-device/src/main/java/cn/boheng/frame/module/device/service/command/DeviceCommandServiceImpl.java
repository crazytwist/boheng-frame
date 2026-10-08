package cn.boheng.frame.module.device.service.command;

import cn.boheng.frame.framework.common.pojo.PageResult;
import cn.boheng.frame.framework.common.util.object.BeanUtils;
import cn.boheng.frame.framework.security.core.util.SecurityFrameworkUtils;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandPageReqVO;
import cn.boheng.frame.module.device.controller.admin.command.vo.DeviceCommandRespVO;
import cn.boheng.frame.module.device.controller.admin.device.vo.DeviceInvokeRespVO;
import cn.boheng.frame.module.device.dal.dataobject.action.DeviceActionDO;
import cn.boheng.frame.module.device.dal.dataobject.analysis.DeviceAnalysisResultDO;
import cn.boheng.frame.module.device.dal.dataobject.codec.DeviceCodecDO;
import cn.boheng.frame.module.device.dal.dataobject.command.DeviceCommandDO;
import cn.boheng.frame.module.device.dal.dataobject.device.DeviceInfoDO;
import cn.boheng.frame.module.device.dal.dataobject.measurement.DeviceMeasurementDO;
import cn.boheng.frame.module.device.dal.dataobject.measurement.DeviceMeasurementDataDO;
import cn.boheng.frame.module.device.dal.dataobject.raw.DeviceDataRawDO;
import cn.boheng.frame.module.device.dal.mysql.analysis.DeviceAnalysisResultMapper;
import cn.boheng.frame.module.device.dal.mysql.codec.DeviceCodecMapper;
import cn.boheng.frame.module.device.dal.mysql.command.DeviceCommandMapper;
import cn.boheng.frame.module.device.dal.mysql.measurement.DeviceMeasurementDataMapper;
import cn.boheng.frame.module.device.dal.mysql.measurement.DeviceMeasurementMapper;
import cn.boheng.frame.module.device.dal.mysql.raw.DeviceDataRawMapper;
import cn.boheng.frame.module.device.service.codec.DeviceCodecParser;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_COMMAND_NOT_EXISTS;

@Service
public class DeviceCommandServiceImpl implements DeviceCommandService {

    @Resource
    private DeviceCommandMapper deviceCommandMapper;
    @Resource
    private DeviceDataRawMapper deviceDataRawMapper;
    @Resource
    private DeviceCodecMapper deviceCodecMapper;
    @Resource
    private DeviceMeasurementMapper deviceMeasurementMapper;
    @Resource
    private DeviceMeasurementDataMapper deviceMeasurementDataMapper;
    @Resource
    private DeviceAnalysisResultMapper deviceAnalysisResultMapper;

    @Override
    public DeviceCommandDO open(DeviceInfoDO device, DeviceActionDO action, String paramsJson, String codecCode) {
        DeviceCommandDO row = new DeviceCommandDO();
        row.setCommandNo("C" + IdUtil.getSnowflakeNextIdStr());
        row.setSourceType("MANUAL");
        row.setAttempt(1);
        row.setDeviceId(device.getId());
        row.setDeviceCode(device.getDeviceCode());
        row.setDeviceTypeCode(device.getDeviceTypeCode());
        row.setActionCode(action.getActionCode());
        row.setDispatchMode("SYNC");
        row.setResultMode("SYNC_RETURN");
        row.setPollCount(0);
        row.setStatus("ACQUIRED");
        row.setParamsJson(jsonText(paramsJson));
        row.setCodecCode(StrUtil.trimToNull(codecCode));
        row.setOperator(StrUtil.blankToDefault(SecurityFrameworkUtils.getLoginUserNickname(), "user"));
        row.setOperatorType("USER");
        row.setAcquiredAt(LocalDateTime.now());
        row.setVersion(0);
        deviceCommandMapper.insert(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long commandId, DeviceInvokeRespVO resp, String codecCode) {
        DeviceCommandDO command = deviceCommandMapper.selectById(commandId);
        if (command == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean ok = resp.getStatus() != null && resp.getStatus() >= 200 && resp.getStatus() < 300;
        command.setStatus(ok ? "SUCCEEDED" : "FAILED");
        command.setSentAt(now);
        command.setFinishedAt(now);
        command.setCodecCode(StrUtil.blankToDefault(StrUtil.trimToNull(codecCode), command.getCodecCode()));
        command.setRequestJson(envelope("method", resp.getHttpMethod(), "url", resp.getRequestUrl(), "body", resp.getRequestBody()));
        command.setResponseJson(envelope("status", resp.getStatus(), "contentType", resp.getContentType(), "body", resp.getBody()));
        command.setPayloadJson(jsonText(resp.getRequestBody()));
        if (!ok) {
            command.setErrorMsg(limit(StrUtil.blankToDefault(resp.getBody(), "HTTP " + resp.getStatus()), 1000));
        }

        DeviceDataRawDO raw = new DeviceDataRawDO();
        raw.setCommandId(command.getId());
        raw.setDeviceId(command.getDeviceId());
        raw.setDeviceCode(command.getDeviceCode());
        raw.setDataType("HTTP_RESPONSE");
        raw.setRawJson(envelope("body", resp.getBody()));
        raw.setMimeType(limit(resp.getContentType(), 64));
        raw.setFormat(formatOf(resp.getContentType(), resp.getBody()));
        raw.setReceivedTime(now);
        deviceDataRawMapper.insert(raw);
        command.setDataRawId(raw.getId());

        if (ok && StrUtil.isNotBlank(command.getCodecCode())) {
            applyCodec(command, raw);
        }
        deviceCommandMapper.updateById(command);
    }

    @Override
    public void fail(Long commandId, String message) {
        DeviceCommandDO command = deviceCommandMapper.selectById(commandId);
        if (command == null || isTerminal(command.getStatus())) {
            return;
        }
        command.setStatus("FAILED");
        command.setFinishedAt(LocalDateTime.now());
        command.setErrorMsg(limit(message, 1000));
        deviceCommandMapper.updateById(command);
    }

    @Override
    public PageResult<DeviceCommandRespVO> getCommandPage(DeviceCommandPageReqVO pageReqVO) {
        PageResult<DeviceCommandDO> page = deviceCommandMapper.selectPage(pageReqVO);
        return BeanUtils.toBean(page, DeviceCommandRespVO.class);
    }

    @Override
    public DeviceCommandRespVO getCommand(Long id) {
        DeviceCommandDO command = deviceCommandMapper.selectById(id);
        if (command == null) {
            throw exception(DEVICE_COMMAND_NOT_EXISTS);
        }
        DeviceCommandRespVO vo = BeanUtils.toBean(command, DeviceCommandRespVO.class);
        if (command.getDataRawId() != null) {
            DeviceDataRawDO raw = deviceDataRawMapper.selectById(command.getDataRawId());
            if (raw != null) {
                vo.setRawFormat(raw.getFormat());
                vo.setRawBody(textOf(raw.getRawJson()));
            }
        }
        return vo;
    }

    private void applyCodec(DeviceCommandDO command, DeviceDataRawDO raw) {
        try {
            DeviceCodecDO codec = deviceCodecMapper.selectByCodecCode(command.getCodecCode());
            if (codec == null || (codec.getStatus() != null && codec.getStatus() != 0)) {
                command.setErrorMsg("响应已保存，解析规则不可用");
                return;
            }
            String body = textOf(raw.getRawJson());
            JSONObject parsed = DeviceCodecParser.parse(codec.getParseType(), codec.getFieldMapping(), codec.getRegexPattern(), body);
            ingest(command, raw, parsed);
        } catch (Exception ex) {
            command.setErrorMsg(limit("响应已保存，解析失败：" + ex.getMessage(), 1000));
        }
    }

    private void ingest(DeviceCommandDO command, DeviceDataRawDO raw, JSONObject parsed) {
        DeviceMeasurementDO measurement = new DeviceMeasurementDO();
        measurement.setCommandId(command.getId());
        measurement.setDataRawId(raw.getId());
        measurement.setDeviceId(command.getDeviceId());
        measurement.setDeviceCode(command.getDeviceCode());
        measurement.setMeasureMode(firstString(parsed, "measureMode", "mode"));
        measurement.setWavelengthNm(asInt(first(parsed, "wavelengthNm", "wavelength")));
        measurement.setScriptName(firstString(parsed, "scriptName"));
        measurement.setMethodParamsJson(command.getParamsJson());
        measurement.setOperator(command.getOperator());
        measurement.setOperatorType("USER");
        measurement.setReadTime(LocalDateTime.now());
        deviceMeasurementMapper.insert(measurement);

        Object wells = parsed.get("wells");
        if (wells instanceof JSONArray array) {
            for (Object item : array) {
                if (item instanceof JSONObject object) {
                    insertWell(measurement.getId(), object, measurement.getWavelengthNm());
                }
            }
        } else if (StrUtil.isNotBlank(firstString(parsed, "wellPosition", "well", "position"))) {
            insertWell(measurement.getId(), parsed, measurement.getWavelengthNm());
        }

        String resultType = firstString(parsed, "resultType");
        if (StrUtil.isNotBlank(resultType)) {
            DeviceAnalysisResultDO analysis = new DeviceAnalysisResultDO();
            analysis.setMeasurementId(measurement.getId());
            analysis.setDeviceId(command.getDeviceId());
            analysis.setResultType(resultType);
            analysis.setResultValue(firstString(parsed, "resultValue"));
            analysis.setSourceSystem("INTERNAL");
            analysis.setSummaryJson(parsed.toString());
            deviceAnalysisResultMapper.insert(analysis);
        }
    }

    private void insertWell(Long measurementId, JSONObject object, Integer fallbackWavelength) {
        String position = firstString(object, "wellPosition", "well", "position");
        if (StrUtil.isBlank(position)) {
            return;
        }
        DeviceMeasurementDataDO row = new DeviceMeasurementDataDO();
        row.setMeasurementId(measurementId);
        row.setWellPosition(position);
        fillIndex(row, position);
        row.setWavelengthNm(asInt(first(object, "wavelengthNm", "wavelength")));
        if (row.getWavelengthNm() == null) {
            row.setWavelengthNm(fallbackWavelength);
        }
        row.setReadValue(asDecimal(first(object, "readValue", "value", "od")));
        row.setUnit(firstString(object, "unit"));
        row.setRawText(limit(firstString(object, "rawText"), 128));
        row.setQualityFlag(firstString(object, "qualityFlag"));
        deviceMeasurementDataMapper.insert(row);
    }

    private static void fillIndex(DeviceMeasurementDataDO row, String position) {
        if (!position.matches("(?i)[A-Z]+\\d+")) {
            return;
        }
        String letters = position.replaceAll("\\d", "").toUpperCase();
        String digits = position.replaceAll("\\D", "");
        int index = 0;
        for (char letter : letters.toCharArray()) {
            index = index * 26 + (letter - 'A' + 1);
        }
        row.setRowIndex(index);
        row.setColIndex(Integer.parseInt(digits));
    }

    private static boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "TIMED_OUT".equals(status) || "CANCELLED".equals(status);
    }

    private static String envelope(Object... pairs) {
        JSONObject object = new JSONObject();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            object.set(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return object.toString();
    }

    private static String jsonText(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        String trimmed = text.trim();
        if (JSONUtil.isTypeJSON(trimmed)) {
            return trimmed;
        }
        return envelope("text", text);
    }

    private static String textOf(String rawJson) {
        if (StrUtil.isBlank(rawJson) || !JSONUtil.isTypeJSONObject(rawJson)) {
            return rawJson;
        }
        JSONObject object = JSONUtil.parseObj(rawJson);
        if (object.containsKey("body")) {
            Object body = object.get("body");
            return body == null ? "" : String.valueOf(body);
        }
        return rawJson;
    }

    private static String formatOf(String contentType, String body) {
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (type.contains("xml")) {
            return "XML";
        }
        if (type.contains("json") || (body != null && JSONUtil.isTypeJSON(body.trim()))) {
            return "JSON";
        }
        if (type.contains("csv")) {
            return "CSV";
        }
        return "TEXT";
    }

    private static Object first(JSONObject object, String... keys) {
        for (String key : keys) {
            if (object.containsKey(key) && object.get(key) != null) {
                return object.get(key);
            }
        }
        return null;
    }

    private static String firstString(JSONObject object, String... keys) {
        Object value = first(object, keys);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInt(Object value) {
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value)).intValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal asDecimal(Object value) {
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String limit(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

}
