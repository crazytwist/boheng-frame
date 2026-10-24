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
import cn.boheng.frame.module.device.dal.mysql.device.DeviceInfoMapper;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static cn.boheng.frame.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.boheng.frame.module.device.enums.ErrorCodeConstants.DEVICE_COMMAND_NOT_EXISTS;
/**
 * 命令记录服务实现
 */

@Service
public class DeviceCommandServiceImpl implements DeviceCommandService {
    /**
     * 命令记录表
     */

    @Resource
    private DeviceCommandMapper deviceCommandMapper;
    /**
     * 原始响应表
     */
    @Resource
    private DeviceDataRawMapper deviceDataRawMapper;
    /**
     * 解析规则表
     */
    @Resource
    private DeviceCodecMapper deviceCodecMapper;
    /**
     * 测量记录表
     */
    @Resource
    private DeviceMeasurementMapper deviceMeasurementMapper;
    /**
     * 孔级读数表
     */
    @Resource
    private DeviceMeasurementDataMapper deviceMeasurementDataMapper;
    /**
     * 分析结论表
     */
    @Resource
    private DeviceAnalysisResultMapper deviceAnalysisResultMapper;

    /**
     * 设备台账。异步命令进入终态后释放对应设备占用
     */
    @Resource
    private DeviceInfoMapper deviceInfoMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;
    /**
     * 调用开始时落一条已占用的命令
     */

    @Override
    public DeviceCommandDO open(DeviceInfoDO device, DeviceActionDO action, String paramsJson, String codecCode) {
        return open(device, action, paramsJson, codecCode, CommandSource.manual());
    }

    /**
     * 带来源的落命令。流程引擎的命令按 FLOW 记，事后能沿 source_ref 找回是哪个节点哪次尝试发的
     */
    @Override
    public DeviceCommandDO open(DeviceInfoDO device, DeviceActionDO action, String paramsJson, String codecCode,
                                CommandSource source) {
        CommandSource src = source == null ? CommandSource.manual() : source;
        DeviceCommandDO row = new DeviceCommandDO();
        row.setCommandNo("C" + IdUtil.getSnowflakeNextIdStr());
        row.setSourceType(StrUtil.blankToDefault(src.sourceType(), "MANUAL"));
        row.setSourceRef(StrUtil.trimToNull(src.sourceRef()));
        row.setAttempt(src.attempt() == null || src.attempt() < 1 ? 1 : src.attempt());
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
        row.setOperator(StrUtil.blankToDefault(src.operator(),
                StrUtil.blankToDefault(SecurityFrameworkUtils.getLoginUserNickname(), "user")));
        row.setOperatorType("USER");
        row.setAcquiredAt(LocalDateTime.now());
        deviceCommandMapper.insert(row);
        return row;
    }
    /**
     * 写入请求、响应和原始数据；配了解析规则时再生成测量
     */

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

        DeviceDataRawDO raw = saveRaw(command, "HTTP_RESPONSE", resp.getBody(), resp.getContentType(), now);
        if (ok && StrUtil.isNotBlank(command.getCodecCode())) {
            applyCodec(command, raw);
        }
        deviceCommandMapper.updateById(command);
        publishCompleted(command);
    }

    /**
     * 发请求之前就转入等回调，设备回调得比受理应答还快时也能对上
     */
    @Override
    public void markWaitingCallback(Long commandId, String tokenHash, LocalDateTime timeoutAt) {
        DeviceCommandDO command = deviceCommandMapper.selectById(commandId);
        if (command == null || isTerminal(command.getStatus())) {
            return;
        }
        command.setDispatchMode("ASYNC");
        command.setResultMode("CALLBACK");
        command.setStatus("WAITING");
        command.setWaitingFor("DEVICE_CALLBACK");
        command.setSentAt(LocalDateTime.now());
        command.setTimeoutAt(timeoutAt);
        command.setCallbackTokenHash(tokenHash);
        deviceCommandMapper.updateById(command);
    }

    /**
     * 只写请求列。响应列归回调，两边并发也互不覆盖。
     */
    @Override
    public void recordSent(Long commandId, DeviceInvokeRespVO ack) {
        DeviceCommandDO update = new DeviceCommandDO();
        update.setId(commandId);
        update.setRequestJson(envelope("method", ack.getHttpMethod(), "url", ack.getRequestUrl(), "body", ack.getRequestBody(),
                "ack", ackOf(ack.getStatus(), ack.getContentType(), ack.getBody())));
        update.setPayloadJson(jsonText(ack.getRequestBody()));
        deviceCommandMapper.updateById(update);
    }

    /**
     * 回调正文按响应处理：落原始数据、按解析规则生成测量。先抢状态，抢不到说明已超时或已收过一次。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeCallback(Long commandId, String body, String contentType) {
        DeviceCommandDO command = deviceCommandMapper.selectById(commandId);
        if (command == null || !"WAITING".equals(command.getStatus())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        String failure = callbackFailure(body);
        DeviceCommandDO claim = new DeviceCommandDO();
        claim.setId(commandId);
        claim.setStatus(failure == null ? "SUCCEEDED" : "FAILED");
        claim.setWaitingFor("");
        claim.setFinishedAt(now);
        if (!deviceCommandMapper.updateIfStatus(claim, "WAITING")) {
            return false;
        }
        command.setErrorMsg(failure == null ? null : limit(failure, 1000));
        DeviceDataRawDO raw = saveRaw(command, "HTTP_CALLBACK", body, contentType, now);
        if (failure == null && StrUtil.isNotBlank(command.getCodecCode())) {
            applyCodec(command, raw);
        }
        DeviceCommandDO result = new DeviceCommandDO();
        result.setId(commandId);
        result.setResponseJson(envelope("status", 200, "contentType", contentType, "body", body));
        result.setDataRawId(command.getDataRawId());
        result.setErrorMsg(command.getErrorMsg());
        deviceCommandMapper.updateById(result);
        deviceInfoMapper.releaseOccupy(command.getDeviceId(), command.getId());
        command.setStatus(failure == null ? "SUCCEEDED" : "FAILED");
        publishCompleted(command);
        return true;
    }

    /**
     * 到期还在等回调的命令标成超时。回调先到时不覆盖。
     */
    @Override
    public void timeout(Long commandId, String message) {
        DeviceCommandDO command = deviceCommandMapper.selectById(commandId);
        if (command == null) {
            return;
        }
        DeviceCommandDO update = new DeviceCommandDO();
        update.setId(commandId);
        update.setStatus("TIMED_OUT");
        update.setWaitingFor("");
        update.setFinishedAt(LocalDateTime.now());
        update.setErrorMsg(limit(message, 1000));
        if (deviceCommandMapper.updateIfStatus(update, "WAITING")) {
            deviceInfoMapper.releaseOccupy(command.getDeviceId(), command.getId());
            command.setStatus("TIMED_OUT");
            publishCompleted(command);
        }
    }

    /**
     * 命令当前状态，不存在时返回空
     */
    @Override
    public String getStatus(Long commandId) {
        DeviceCommandDO command = deviceCommandMapper.selectById(commandId);
        return command == null ? null : command.getStatus();
    }

    /**
     * 写一条原始数据，并挂到命令上
     */
    private DeviceDataRawDO saveRaw(DeviceCommandDO command, String dataType, String body, String contentType,
                                    LocalDateTime now) {
        DeviceDataRawDO raw = new DeviceDataRawDO();
        raw.setCommandId(command.getId());
        raw.setDeviceId(command.getDeviceId());
        raw.setDeviceCode(command.getDeviceCode());
        raw.setDataType(dataType);
        raw.setRawJson(envelope("body", body));
        raw.setMimeType(limit(contentType, 64));
        raw.setFormat(formatOf(contentType, body));
        raw.setReceivedTime(now);
        deviceDataRawMapper.insert(raw);
        command.setDataRawId(raw.getId());
        return raw;
    }

    /**
     * 受理应答写进响应记录时的样子
     */
    private static JSONObject ackOf(Integer status, String contentType, String body) {
        JSONObject ack = new JSONObject();
        ack.set("status", status);
        ack.set("contentType", contentType);
        ack.set("body", body);
        return ack;
    }

    /**
     * 回调正文里明确写了失败时返回原因，否则返回空。认 status 为 FAILED/ERROR/FAIL，或 success 为 false。
     */
    private static String callbackFailure(String body) {
        if (StrUtil.isBlank(body) || !JSONUtil.isTypeJSONObject(body.trim())) {
            return null;
        }
        JSONObject object = JSONUtil.parseObj(body);
        String status = object.getStr("status");
        boolean failed = "FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status) || "FAIL".equalsIgnoreCase(status)
                || Boolean.FALSE.equals(object.getBool("success"));
        if (!failed) {
            return null;
        }
        return "设备回调失败：" + StrUtil.blankToDefault(firstString(object, "message", "msg", "error"), body);
    }
    /**
     * 调用没有完成时把命令标成失败
     */

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
        publishCompleted(command);
    }

    /** 发布统一终态事件；流程节点按 commandId 关联，独立设备命令则无人消费。 */
    private void publishCompleted(DeviceCommandDO command) {
        eventPublisher.publishEvent(new cn.boheng.frame.module.device.api.dto.DeviceCommandCompletedEvent(
                command.getId(), command.getCommandNo(), command.getStatus()));
    }

    /**
     * 按这次仿真调用记下发方式和结果回收方式
     */
    @Override
    public void bindMode(Long commandId, String dispatchMode, String resultMode) {
        DeviceCommandDO command = deviceCommandMapper.selectById(commandId);
        if (command == null) {
            throw exception(DEVICE_COMMAND_NOT_EXISTS);
        }
        command.setDispatchMode(dispatchMode);
        command.setResultMode(resultMode);
        deviceCommandMapper.updateById(command);
    }

    /**
     * 记下已轮询次数。还没拿到结果时命令停在等待轮询。
     */
    @Override
    public void recordPoll(Long commandId, int pollCount, boolean done) {
        DeviceCommandDO command = deviceCommandMapper.selectById(commandId);
        if (command == null || isTerminal(command.getStatus())) {
            return;
        }
        command.setPollCount(pollCount);
        command.setWaitingFor(done ? "" : "POLL_TICK");
        if (!done) {
            command.setStatus("WAITING");
        }
        deviceCommandMapper.updateById(command);
    }

    /**
     * 命令记录分页
     */

    @Override
    public PageResult<DeviceCommandRespVO> getCommandPage(DeviceCommandPageReqVO pageReqVO) {
        PageResult<DeviceCommandDO> page = deviceCommandMapper.selectPage(pageReqVO);
        return BeanUtils.toBean(page, DeviceCommandRespVO.class);
    }
    /**
     * 命令详情，带上原始响应正文
     */

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

    /**
     * 用解析规则读响应。解析失败只记在命令上，原始响应保留
     */
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

    /**
     * 把解析结果写成测量、孔位读数和分析结论
     */
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
        } else if (StrUtil.isNotBlank(firstString(parsed, "wellPosition", "well", "position", "parentPositionCode", "instanceName"))) {
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

    /**
     * 写入一个孔的读数。孔位认 wellPosition、parentPositionCode 或 instanceName，没有孔位则跳过。
     * 读数认 readValue 或 currentVolUl，体积没有单位时记为 µL。
     */
    private void insertWell(Long measurementId, JSONObject object, Integer fallbackWavelength) {
        String position = firstString(object, "wellPosition", "well", "position", "parentPositionCode", "instanceName");
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
        Object volume = first(object, "readValue", "value", "od", "currentVolUl", "currentCount");
        row.setReadValue(asDecimal(volume));
        String unit = firstString(object, "unit");
        if (StrUtil.isBlank(unit) && object.get("currentVolUl") != null) {
            unit = "µL";
        }
        row.setUnit(unit);
        row.setRawText(limit(firstString(object, "rawText", "instanceCode"), 128));
        row.setQualityFlag(firstString(object, "qualityFlag", "instanceStatus"));
        deviceMeasurementDataMapper.insert(row);
    }

    /**
     * 从 A1 这种孔位算出行号和列号
     */
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

    /**
     * 命令是否已经进入终态
     */
    private static boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status) || "TIMED_OUT".equals(status) || "CANCELLED".equals(status);
    }

    /**
     * 拼成可以写入 JSON 列的对象
     */
    private static String envelope(Object... pairs) {
        JSONObject object = new JSONObject();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            object.set(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return object.toString();
    }

    /**
     * 原文已是 JSON 则原样保存，否则包进 text 字段
     */
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

    /**
     * 从保存的原始 JSON 里取出响应正文
     */
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

    /**
     * 根据 Content-Type 判断结果格式
     */
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

    /**
     * 按给出的字段名顺序，取第一个有值的字段
     */
    private static Object first(JSONObject object, String... keys) {
        for (String key : keys) {
            if (object.containsKey(key) && object.get(key) != null) {
                return object.get(key);
            }
        }
        return null;
    }

    /**
     * 按字段名顺序取第一个有值的字段，并转成字符串
     */
    private static String firstString(JSONObject object, String... keys) {
        Object value = first(object, keys);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 转成整数。转不了时返回空
     */
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

    /**
     * 转成小数。转不了时返回空
     */
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

    /**
     * 截断到数据库列能放下的长度
     */
    private static String limit(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

}
