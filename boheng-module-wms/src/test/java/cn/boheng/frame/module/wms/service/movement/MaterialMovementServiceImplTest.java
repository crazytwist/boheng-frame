package cn.boheng.frame.module.wms.service.movement;

import cn.boheng.frame.framework.common.exception.ServiceException;
import cn.boheng.frame.module.wms.dal.dataobject.movement.MaterialMovementDO;
import cn.boheng.frame.module.wms.dal.mysql.movement.MaterialMovementMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static cn.boheng.frame.module.wms.enums.ErrorCodeConstants.MOVEMENT_IDEMPOTENT_DUPLICATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流水幂等预查（doc/06 D1-a / Phase C）：行键已存在或撞 uk_idempotent 时抛
 * {@code MOVEMENT_IDEMPOTENT_DUPLICATE}，供消耗业务层回放。
 */
class MaterialMovementServiceImplTest {

    private final AtomicReference<MaterialMovementDO> byKey = new AtomicReference<>();
    private final AtomicInteger insertCount = new AtomicInteger();
    private final List<MaterialMovementDO> inserted = new ArrayList<>();
    private boolean insertThrowsDuplicate;

    private MaterialMovementServiceImpl service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, MaterialMovementDO.class);
    }

    @BeforeEach
    void setUp() {
        byKey.set(null);
        insertCount.set(0);
        inserted.clear();
        insertThrowsDuplicate = false;
        service = new MaterialMovementServiceImpl();
        ReflectionTestUtils.setField(service, "materialMovementMapper", mapper());
    }

    @Test
    void recordMovementInsertsWhenKeyAbsent() {
        MaterialMovementDO movement = baseMovement("flow:1:n:0:1:consume#0");
        Long id = service.recordMovement(movement);
        assertEquals(1, insertCount.get());
        assertEquals(inserted.get(0).getId(), id);
        assertEquals("MANUAL", inserted.get(0).getBizSource());
        assertEquals("USER", inserted.get(0).getOperatorType());
    }

    @Test
    void recordMovementPrecheckThrowsWhenKeyExists() {
        MaterialMovementDO existing = baseMovement("k#0");
        existing.setId(88L);
        byKey.set(existing);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.recordMovement(baseMovement("k#0")));
        assertEquals(MOVEMENT_IDEMPOTENT_DUPLICATE.getCode(), ex.getCode());
        assertEquals(0, insertCount.get());
    }

    @Test
    void recordMovementDuplicateKeyThrowsIdempotentDuplicate() {
        insertThrowsDuplicate = true;
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.recordMovement(baseMovement("race#0")));
        assertEquals(MOVEMENT_IDEMPOTENT_DUPLICATE.getCode(), ex.getCode());
        assertTrue(insertCount.get() >= 1);
    }

    @Test
    void getByIdempotentKeyDelegates() {
        MaterialMovementDO existing = baseMovement("k#1");
        existing.setId(9L);
        byKey.set(existing);
        assertEquals(9L, service.getByIdempotentKey("k#1").getId());
    }

    private static MaterialMovementDO baseMovement(String key) {
        return MaterialMovementDO.builder()
                .movementType("CONSUME")
                .instanceId(1L)
                .instanceCode("INST-1")
                .idempotentKey(key)
                .build();
    }

    private MaterialMovementMapper mapper() {
        return (MaterialMovementMapper) Proxy.newProxyInstance(
                MaterialMovementMapper.class.getClassLoader(),
                new Class[]{MaterialMovementMapper.class},
                (p, m, a) -> {
                    String name = m.getName();
                    if ("toString".equals(name)) {
                        return "MaterialMovementMapperProxy";
                    }
                    if ("selectByIdempotentKey".equals(name)) {
                        String key = (String) a[0];
                        MaterialMovementDO hit = byKey.get();
                        return hit != null && key != null && key.equals(hit.getIdempotentKey()) ? hit : null;
                    }
                    if ("insert".equals(name)) {
                        insertCount.incrementAndGet();
                        if (insertThrowsDuplicate) {
                            throw new DuplicateKeyException("Duplicate entry for key 'uk_idempotent'");
                        }
                        MaterialMovementDO row = (MaterialMovementDO) a[0];
                        if (row.getId() == null) {
                            row.setId(1000L + inserted.size());
                        }
                        inserted.add(row);
                        byKey.set(row);
                        return 1;
                    }
                    throw new UnsupportedOperationException(name);
                });
    }
}
