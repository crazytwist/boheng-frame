package cn.boheng.frame.module.flow.service.relation;

import cn.boheng.frame.module.flow.controller.admin.relation.vo.FlowRelationUpdateReqVO;
import cn.boheng.frame.module.flow.dal.dataobject.definition.FlowNodeDO;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 节点运行配置的合并语义。这里钉死三条约定：
 * <ol>
 *   <li>null = 这次不动这个键，已有的超时和断言不能被顺手抹掉；</li>
 *   <li>0 / 空串 = 清掉这层覆盖、回落步骤表；</li>
 *   <li>断言只改一部分键时，别的键必须原样留着。</li>
 * </ol>
 * 第 1、3 条防的是「改个坐标把断点判定搞丢」这类静默事故——它不会报错，
 * 只会在某天发现流程莫名其妙地不再拦不合格结果。
 */
class FlowRelationRunConfigTest {

    /**
     * mergeRunConfig 是私有实现细节，但语义重要到值得直接钉住
     */
    private static void merge(FlowNodeDO row, FlowRelationUpdateReqVO reqVO) {
        try {
            FlowRelationServiceImpl service = new FlowRelationServiceImpl();
            Method method = FlowRelationServiceImpl.class
                    .getDeclaredMethod("mergeRunConfig", FlowNodeDO.class, FlowRelationUpdateReqVO.class);
            method.setAccessible(true);
            method.invoke(service, row, reqVO);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static FlowNodeDO nodeWith(String runConfig) {
        FlowNodeDO row = new FlowNodeDO();
        row.setNodeId("weigh");
        row.setRunConfig(runConfig);
        return row;
    }

    @Test
    void untouchedFieldsKeepTheirValue() {
        FlowNodeDO row = nodeWith("{\"timeoutMs\":600000,\"completeWhen\":\"output.weight >= 0.95\"}");
        // 只改坐标这类请求，四个运行配置字段都是 null
        merge(row, new FlowRelationUpdateReqVO());
        assertEquals("{\"timeoutMs\":600000,\"completeWhen\":\"output.weight >= 0.95\"}",
                row.getRunConfig());
    }

    @Test
    void partialUpdateKeepsOtherKeys() {
        FlowNodeDO row = nodeWith("{\"timeoutMs\":600000,\"completeWhen\":\"output.weight >= 0.95\"}");
        FlowRelationUpdateReqVO req = new FlowRelationUpdateReqVO();
        req.setMaxAttempts(3);
        merge(row, req);
        String json = row.getRunConfig();
        assertTrue(json.contains("\"maxAttempts\":3"), json);
        // 断言必须还在——这是本测试的核心
        assertTrue(json.contains("output.weight >= 0.95"), json);
        assertTrue(json.contains("\"timeoutMs\":600000"), json);
    }

    @Test
    void zeroClearsTheOverride() {
        FlowNodeDO row = nodeWith("{\"timeoutMs\":600000,\"maxAttempts\":3}");
        FlowRelationUpdateReqVO req = new FlowRelationUpdateReqVO();
        req.setTimeoutSec(0);
        req.setMaxAttempts(0);
        merge(row, req);
        // 全清干净了就存 NULL，而不是留个 "{}"
        assertNull(row.getRunConfig());
    }

    @Test
    void timeoutIsStoredAsMilliseconds() {
        FlowNodeDO row = nodeWith(null);
        FlowRelationUpdateReqVO req = new FlowRelationUpdateReqVO();
        req.setTimeoutSec(90);
        merge(row, req);
        // 列里与 flow_step.timeout_ms 同单位，对外暴露秒
        assertEquals(90000L, JSONUtil.parseObj(row.getRunConfig()).getLong("timeoutMs"));
    }

    @Test
    void blankCompleteWhenClearsAssertion() {
        FlowNodeDO row = nodeWith("{\"completeWhen\":\"output.weight >= 0.95\"}");
        FlowRelationUpdateReqVO req = new FlowRelationUpdateReqVO();
        req.setCompleteWhen("   ");
        merge(row, req);
        assertNull(row.getRunConfig());
    }

    @Test
    void brokenAssertionIsRejectedBeforeSaving() {
        FlowNodeDO row = nodeWith(null);
        FlowRelationUpdateReqVO req = new FlowRelationUpdateReqVO();
        // 括号没闭合。发布期也会拦，但这里就该拦——用户还在这个节点上
        req.setCompleteWhen("output.weight >= (0.95");
        assertThrows(Exception.class, () -> merge(row, req));
    }

    @Test
    void corruptExistingConfigDoesNotPoisonTheSave() {
        FlowNodeDO row = nodeWith("{这不是 json");
        FlowRelationUpdateReqVO req = new FlowRelationUpdateReqVO();
        req.setMaxAttempts(2);
        // 坏数据当成空对象从头合并，不能因为历史脏数据就让这次保存全废
        merge(row, req);
        assertEquals(2, JSONUtil.parseObj(row.getRunConfig()).getInt("maxAttempts"));
    }

}
