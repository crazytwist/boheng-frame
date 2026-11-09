-- D02：为已有 device_command 表补齐与 device.sql 一致的 uk_source
--      UNIQUE KEY uk_source (tenant_id, source_type, source_ref)
-- 新库请直接使用 device.sql（已含 uk_source）；本脚本仅用于已有库迁移。
-- 执行前确认同租户的非空 source_type/source_ref 无重复记录。
-- 人工命令由服务生成独立的 manual:commandNo 来源键。
SELECT tenant_id, source_type, source_ref, COUNT(*) AS duplicate_count
FROM device_command
WHERE source_type IS NOT NULL AND source_ref IS NOT NULL
GROUP BY tenant_id, source_type, source_ref
HAVING COUNT(*) > 1;

-- 上述查询无结果后执行；保留旧 idx_source 不影响正确性，后续可择期删除。
ALTER TABLE device_command
    ADD UNIQUE KEY uk_source (tenant_id, source_type, source_ref);
