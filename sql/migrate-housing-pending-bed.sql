-- 一次性迁移：允许待审核（PENDING）的入住申请记录学生选择的床位。
-- 仅放宽 housing 的 CHECK 约束，不新增字段或状态；全新数据库已包含此约束，无需执行。
USE dorm_management;

ALTER TABLE housing DROP CHECK housing_chk_2;
ALTER TABLE housing ADD CONSTRAINT housing_pending_bed
  CHECK (status = 'PENDING' OR
    (status IN ('ASSIGNED', 'CHECKED_IN', 'CHECKED_OUT')
     AND bed_id IS NOT NULL));
