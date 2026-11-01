package cn.boheng.frame.module.wms.service.sample;

import cn.boheng.frame.framework.common.exception.ServiceException;
import cn.boheng.frame.module.wms.controller.admin.sample.vo.SampleInfoSaveReqVO;
import cn.boheng.frame.module.wms.dal.dataobject.sample.SampleInfoDO;
import cn.boheng.frame.module.wms.dal.mysql.instance.MaterialInstanceMapper;
import cn.boheng.frame.module.wms.dal.mysql.sample.SampleInfoMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.SAMPLE_ID_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 样本登记同态幂等（doc/06 W-1.2）：同 sampleId 关键属性一致回放；不一致 CONFLICT；
 * 并发撞 uk 走同一分支。不启动 Spring，Proxy 桩 Mapper。
 */
class SampleInfoServiceImplTest {

    private final AtomicReference<SampleInfoDO> bySampleId = new AtomicReference<>();
    private final AtomicInteger insertCount = new AtomicInteger();
    private final List<SampleInfoDO> inserted = new ArrayList<>();
    private boolean insertThrowsDuplicate;

    private SampleInfoServiceImpl service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SampleInfoDO.class);
    }

    @BeforeEach
    void setUp() {
        bySampleId.set(null);
        insertCount.set(0);
        inserted.clear();
        insertThrowsDuplicate = false;
        service = new SampleInfoServiceImpl();
        ReflectionTestUtils.setField(service, "sampleInfoMapper", sampleMapper());
        ReflectionTestUtils.setField(service, "materialInstanceMapper",
                Proxy.newProxyInstance(MaterialInstanceMapper.class.getClassLoader(),
                        new Class[]{MaterialInstanceMapper.class},
                        (p, m, a) -> {
                            if ("toString".equals(m.getName())) {
                                return "MaterialInstanceMapperProxy";
                            }
                            throw new UnsupportedOperationException(m.getName());
                        }));
    }

    @Test
    void createSampleInsertsWhenAbsent() {
        SampleInfoSaveReqVO req = baseReq("SP-T-001");
        Long id = service.createSample(req);
        assertEquals(1, insertCount.get());
        assertEquals(inserted.get(0).getId(), id);
        assertEquals("SP-T-001", inserted.get(0).getSampleId());
        assertEquals("ORIGINAL", inserted.get(0).getDeriveType());
    }

    @Test
    void createSampleIdempotentWhenKeyFieldsMatch() {
        SampleInfoDO existing = existingRow(9001L, "SP-T-001", "WATER", "C1", "TUBE",
                new BigDecimal("10.00"), "ORD-1");
        bySampleId.set(existing);

        SampleInfoSaveReqVO req = baseReq("SP-T-001");
        req.setSampleType("WATER");
        req.setContentDefCode("C1");
        req.setContainerTypeCode("TUBE");
        req.setInitialVolUl(new BigDecimal("10.0")); // 标度不同仍应同态
        req.setOrderNo("ORD-1");

        Long id = service.createSample(req);
        assertEquals(9001L, id);
        assertEquals(0, insertCount.get());
    }

    @Test
    void createSampleConflictsWhenKeyFieldsDiffer() {
        SampleInfoDO existing = existingRow(9001L, "SP-T-001", "WATER", "C1", "TUBE",
                new BigDecimal("10"), "ORD-1");
        bySampleId.set(existing);

        SampleInfoSaveReqVO req = baseReq("SP-T-001");
        req.setSampleType("BLOOD"); // 冲突

        ServiceException ex = assertThrows(ServiceException.class, () -> service.createSample(req));
        assertEquals(SAMPLE_ID_CONFLICT.getCode(), ex.getCode());
        assertEquals(0, insertCount.get());
    }

    @Test
    void createSampleDuplicateKeyFallsBackToIdempotent() {
        // 预查为空，insert 撞唯一索引后再查到同态行 → 回放
        insertThrowsDuplicate = true;
        SampleInfoDO raced = existingRow(9002L, "SP-T-002", "WATER", null, null, null, "ORD-2");
        SampleInfoSaveReqVO req = baseReq("SP-T-002");
        req.setOrderNo("ORD-2");

        // insert 抛错后 select 返回 raced
        bySampleId.set(null);
        Long id = service.createSample(req);
        // After duplicate, mapper select returns raced — configure after first select:
        // Our proxy: first select (pre-check) returns null; after failed insert, second select returns raced.
        // Implement via insertThrowsDuplicate flipping the store.
        assertEquals(9002L, id);
        assertEquals(1, insertCount.get());
    }

    @Test
    void sameRegistrationKeyFieldsTreatsBlankAsEqual() {
        SampleInfoDO existing = existingRow(1L, "X", null, "", null, null, " ");
        SampleInfoSaveReqVO req = baseReq("X");
        req.setSampleType("");
        req.setContentDefCode(null);
        req.setContainerTypeCode("  ");
        req.setOrderNo(null);
        assertTrue(service.sameRegistrationKeyFields(existing, req));
    }

    private SampleInfoSaveReqVO baseReq(String sampleId) {
        SampleInfoSaveReqVO req = new SampleInfoSaveReqVO();
        req.setSampleId(sampleId);
        req.setSampleName("n-" + sampleId);
        req.setSampleType("WATER");
        req.setStatus("REGISTERED");
        req.setSource("IMPORT");
        req.setOrderNo("ORD-1");
        return req;
    }

    private static SampleInfoDO existingRow(Long id, String sampleId, String type, String content,
                                           String container, BigDecimal vol, String orderNo) {
        SampleInfoDO row = new SampleInfoDO();
        row.setId(id);
        row.setSampleId(sampleId);
        row.setSampleType(type);
        row.setContentDefCode(content);
        row.setContainerTypeCode(container);
        row.setInitialVolUl(vol);
        row.setOrderNo(orderNo);
        row.setStatus("REGISTERED");
        return row;
    }

    private SampleInfoMapper sampleMapper() {
        AtomicInteger selectCalls = new AtomicInteger();
        return (SampleInfoMapper) Proxy.newProxyInstance(
                SampleInfoMapper.class.getClassLoader(),
                new Class[]{SampleInfoMapper.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("toString".equals(name)) {
                        return "SampleInfoMapperProxy";
                    }
                    if ("selectBySampleId".equals(name)) {
                        int n = selectCalls.incrementAndGet();
                        // 并发兜底场景：预查 null，insert 失败后第二次查到 raced 行
                        if (insertThrowsDuplicate && n >= 2) {
                            return existingRow(9002L, String.valueOf(args[0]), "WATER", null, null, null, "ORD-2");
                        }
                        return bySampleId.get();
                    }
                    if ("insert".equals(name)) {
                        insertCount.incrementAndGet();
                        SampleInfoDO row = (SampleInfoDO) args[0];
                        if (row.getId() == null) {
                            row.setId(1000L + insertCount.get());
                        }
                        if (insertThrowsDuplicate) {
                            throw new DuplicateKeyException("uk_sample_id");
                        }
                        inserted.add(row);
                        bySampleId.set(row);
                        return 1;
                    }
                    throw new UnsupportedOperationException(name);
                });
    }
}
