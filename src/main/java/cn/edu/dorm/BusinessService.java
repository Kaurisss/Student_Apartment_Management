package cn.edu.dorm;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final SessionService sessions;
    public BusinessService(JdbcTemplate db, PasswordEncoder passwords, SessionService sessions) { this.db=db; this.passwords=passwords; this.sessions=sessions; }
    public record Page(List<Map<String,Object>> rows, int page, int pages, long total) {}
    public Map<String,Object> profile(String username) {
        List<Map<String,Object>> rows=db.queryForList("SELECT u.username, s.student_no, s.name FROM student s JOIN `user` u ON u.id=s.user_id WHERE u.username=?", username);
        if (rows.isEmpty()) throw new IllegalArgumentException("该账号尚未关联学生信息，请联系管理员");
        return rows.get(0);
    }
    @Transactional
    public void updateProfile(String username, String name, String studentNo, String password, String oldPassword) {
        List<Map<String,Object>> rows=db.queryForList("SELECT s.id, s.student_no, u.password_hash FROM student s JOIN `user` u ON u.id=s.user_id WHERE u.username=?", username);
        if (rows.isEmpty()) throw new IllegalArgumentException("该账号尚未关联学生信息，请联系管理员");
        var current=rows.get(0);
        long id=((Number)current.get("id")).longValue();
        if (studentNo != null && !studentNo.isBlank() && !studentNo.trim().equals(current.get("student_no")))
            throw new IllegalArgumentException("学号由管理员维护，请联系管理员");
        db.update("UPDATE student SET name=? WHERE id=?", text(name,"姓名",50), id);
        if (password != null && !password.isBlank()) {
            if (password.length() < 6 || password.length() > 100) throw new IllegalArgumentException("密码长度须为6至100个字符");
            if (oldPassword == null || oldPassword.isBlank()) throw new IllegalArgumentException("修改密码需填写原密码");
            if (!passwords.matches(oldPassword, Objects.toString(current.get("password_hash"), ""))) throw new IllegalArgumentException("原密码不正确");
            db.update("UPDATE `user` u JOIN student s ON s.user_id=u.id SET u.password_hash=? WHERE s.id=?", passwords.encode(password), id);
            sessions.expireOthers(username);
        }
    }
    public boolean studentLinked(String username) {
        return db.queryForObject("SELECT COUNT(*) FROM student s JOIN `user` u ON u.id=s.user_id WHERE u.username=?", Integer.class, username) > 0;
    }
    public Long studentId(String username) {
        List<Long> ids=db.query("SELECT s.id FROM student s JOIN `user` u ON u.id=s.user_id WHERE u.username=?", (rs,n)->rs.getLong(1),username);
        if (ids.isEmpty()) throw new IllegalArgumentException("该账号尚未关联学生信息，请联系管理员");
        return ids.get(0);
    }
    public List<Map<String,Object>> students() { return db.queryForList("SELECT id, CONCAT(student_no,' ',name) AS label FROM student ORDER BY student_no"); }
    public List<Map<String,Object>> freeBeds() { return db.queryForList("SELECT b.id, CONCAT(d.building,' ',d.room_no,' / ',b.bed_no,'号床') AS label FROM bed b JOIN dormitory d ON d.id=b.dormitory_id WHERE b.status='FREE' ORDER BY d.building,d.room_no,b.bed_no"); }
    public Map<String,Object> activeHousing(String username) {
        List<Map<String,Object>> rows=db.queryForList("SELECT id, status FROM housing WHERE student_id=? AND status IN ('PENDING','ASSIGNED','CHECKED_IN') ORDER BY id DESC LIMIT 1", studentId(username));
        Map<String,Object> out=new HashMap<>();
        out.put("active", !rows.isEmpty());
        if (!rows.isEmpty()) out.put("status", rows.get(0).get("status"));
        return out;
    }
    public Page list(String type, boolean admin, String username, int requested) {
        boolean repair=type.equals("repairs");
        String table=repair?"repair":"housing";
        String where=admin?"":" WHERE x.student_id=?";
        Object[] countArgs=admin?new Object[]{}:new Object[]{studentId(username)};
        long total=db.queryForObject("SELECT COUNT(*) FROM "+table+" x"+where,Long.class,countArgs);
        int pages=(int)Math.max(1,(total+9)/10),page=Math.max(1,Math.min(requested,pages));
        List<Object> args=new ArrayList<>(Arrays.asList(countArgs)); args.add((page-1)*10);
        String sql="SELECT x.*, s.name AS student_name, s.student_no"+(repair?"":", CONCAT(d.building,' ',d.room_no,' / ',b.bed_no,'号床') AS bed_label")+
            " FROM "+table+" x JOIN student s ON s.id=x.student_id"+(repair?"":" LEFT JOIN bed b ON b.id=x.bed_id LEFT JOIN dormitory d ON d.id=b.dormitory_id")+where+" ORDER BY x.id DESC LIMIT 10 OFFSET ?";
        return new Page(db.queryForList(sql,args.toArray()),page,pages,total);
    }
    public Page repairs(boolean admin, String username, int requested, String keyword, String dorm, String status) {
        StringBuilder where = new StringBuilder(admin ? " WHERE 1=1" : " WHERE x.student_id=?");
        List<Object> args = new ArrayList<>();
        if (!admin) args.add(studentId(username));
        if (status != null && !status.isBlank()) {
            if (!Set.of("WAITING", "IN_PROGRESS", "COMPLETED", "CONFIRMED").contains(status)) throw new IllegalArgumentException("状态无效");
            where.append(" AND x.status=?"); args.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (s.name LIKE ? OR s.student_no LIKE ? OR x.description LIKE ?)");
            String like = "%" + keyword.trim() + "%"; args.add(like); args.add(like); args.add(like);
        }
        if (dorm != null && !dorm.isBlank()) {
            where.append(" AND CONCAT(IFNULL(d.building,''), ' ', IFNULL(d.room_no,'')) LIKE ?");
            args.add("%" + dorm.trim() + "%");
        }
        String from = " FROM repair x JOIN student s ON s.id=x.student_id LEFT JOIN housing h ON h.active_student_id=s.id LEFT JOIN bed b ON b.id=h.bed_id LEFT JOIN dormitory d ON d.id=b.dormitory_id";
        long total = db.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        int pages = (int) Math.max(1, (total + 9) / 10), page = Math.max(1, Math.min(requested, pages));
        args.add((page - 1) * 10);
        String sql = "SELECT x.*, s.name AS student_name, s.student_no, CONCAT(d.building,' ',d.room_no) AS dorm_label" + from + where + " ORDER BY x.id DESC LIMIT 10 OFFSET ?";
        return new Page(db.queryForList(sql, args.toArray()), page, pages, total);
    }
    public Map<String,Object> repairDetail(long id,boolean admin,String username) {
        List<Map<String,Object>> records=db.queryForList("SELECT * FROM repair WHERE id=?",id);
        if (records.isEmpty()) throw new IllegalArgumentException("记录不存在");
        var record=records.get(0); owner(record,admin,username); return record;
    }
    private Map<String,Object> locked(String table,long id) {
        return db.query("SELECT * FROM "+table+" WHERE id=? FOR UPDATE",rs -> {
            if (!rs.next()) throw new IllegalArgumentException("记录不存在");
            Map<String,Object> m=new HashMap<>();
            var meta=rs.getMetaData(); for (int i=1;i<=meta.getColumnCount();i++) m.put(meta.getColumnLabel(i),rs.getObject(i)); return m;
        },id);
    }
    private void owner(Map<String,Object> record,boolean admin,String username) {
        if (!admin && ((Number)record.get("student_id")).longValue()!=studentId(username)) throw new IllegalArgumentException("无权操作其他学生的记录");
    }
    private String text(String value,String label,int max) {
        if (value==null || value.trim().isEmpty()) throw new IllegalArgumentException(label+"不能为空");
        value=value.trim(); if(value.length()>max) throw new IllegalArgumentException(label+"不能超过"+max+"个字符"); return value;
    }
    private void status(Map<String,Object> record,String required) {
        if (!required.equals(record.get("status"))) throw new IllegalArgumentException("记录状态已变化，不能执行该操作");
    }
    private long chosenStudent(boolean admin, String username, Long selected) {
        if (!admin) return studentId(username);
        if (selected==null || selected<1) throw new IllegalArgumentException("请选择学生");
        return selected;
    }
    @Transactional
    public void createRepair(boolean admin,String username,Long selected,String description) {
        long student=chosenStudent(admin,username,selected);
        db.update("INSERT INTO repair(student_id,description,status) VALUES (?,?, 'WAITING')",student,text(description,"故障描述",500));
    }
    @Transactional
    public void editRepair(long id,boolean admin,String username,String description) {
        var record=locked("repair",id); owner(record,admin,username); status(record,"WAITING");
        db.update("UPDATE repair SET description=? WHERE id=?",text(description,"故障描述",500),id);
    }
    @Transactional
    public void deleteRepair(long id,boolean admin,String username) {
        var record=locked("repair",id); owner(record,admin,username); status(record,"WAITING"); db.update("DELETE FROM repair WHERE id=?",id);
    }
    @Transactional
    public void repairAction(long id,String action,boolean admin,String username,String evaluation) {
        var r=locked("repair",id);
        switch(action) {
            case "accept" -> { if(!admin) throw new IllegalArgumentException("仅管理员可受理"); status(r,"WAITING"); db.update("UPDATE repair SET status='IN_PROGRESS' WHERE id=?",id); }
            case "complete" -> { if(!admin) throw new IllegalArgumentException("仅管理员可完成报修"); status(r,"IN_PROGRESS"); db.update("UPDATE repair SET status='COMPLETED' WHERE id=?",id); }
            case "confirm" -> { if(admin) throw new IllegalArgumentException("仅学生可评价"); owner(r,false,username); status(r,"COMPLETED"); db.update("UPDATE repair SET status='CONFIRMED',evaluation=? WHERE id=?",text(evaluation,"评价",500),id); }
            default -> throw new IllegalArgumentException("操作无效");
        }
    }
    @Transactional
    public void createHousing(boolean admin,String username,Long selected,Long bedId) {
        long student=chosenStudent(admin,username,selected);
        // 锁住学生行，使同一学生的并发申请不能同时通过检查。
        locked("student",student);
        if (db.queryForObject("SELECT COUNT(*) FROM housing WHERE student_id=? AND status IN ('PENDING','ASSIGNED','CHECKED_IN')",Integer.class,student)>0)
            throw new IllegalArgumentException("该学生已有未结束的入住申请或入住记录");
        if (bedId==null || bedId<1) throw new IllegalArgumentException("请选择空闲床位");
        var bed=locked("bed",bedId);
        if (!"FREE".equals(bed.get("status"))) throw new IllegalArgumentException("该床位已被占用，请选择其他床位");
        // 申请阶段只记录意向床位，床位仍保持空闲，待管理员确认后再占用。
        db.update("INSERT INTO housing(student_id,bed_id,status) VALUES (?,?,'PENDING')",student,bedId);
    }
    @Transactional
    public void deleteHousing(long id,boolean admin,String username) {
        var record=locked("housing",id); owner(record,admin,username); status(record,"PENDING"); db.update("DELETE FROM housing WHERE id=?",id);
    }
    @Transactional
    public void housingAction(long id,String action,boolean admin,String username,Long bedId) {
        var h=locked("housing",id);
        switch(action) {
            case "assign" -> {
                if(!admin) throw new IllegalArgumentException("仅管理员可分配床位"); status(h,"PENDING");
                if(bedId==null || bedId<1) throw new IllegalArgumentException("请选择空床位");
                long sid=((Number)h.get("student_id")).longValue();
                locked("student",sid);
                if(db.queryForObject("SELECT COUNT(*) FROM housing WHERE student_id=? AND status IN ('ASSIGNED','CHECKED_IN')",Integer.class,sid)>0)
                    throw new IllegalArgumentException("该学生已有有效床位");
                var bed=locked("bed",bedId); status(bed,"FREE");
                db.update("UPDATE bed SET status='OCCUPIED' WHERE id=?",bedId);
                db.update("UPDATE housing SET bed_id=?,status='ASSIGNED' WHERE id=?",bedId,id);
            }
            case "unassign" -> {
                if(!admin) throw new IllegalArgumentException("仅管理员可取消分配"); status(h,"ASSIGNED");
                long bid=((Number)h.get("bed_id")).longValue(); var bed=locked("bed",bid); status(bed,"OCCUPIED");
                db.update("UPDATE housing SET bed_id=NULL,status='PENDING' WHERE id=?",id);
                db.update("UPDATE bed SET status='FREE' WHERE id=?",bid);
            }
            case "checkin" -> {
                if(!admin) throw new IllegalArgumentException("仅管理员可办理入住"); status(h,"ASSIGNED");
                db.update("UPDATE housing SET status='CHECKED_IN', check_in_at=NOW() WHERE id=?",id);
            }
            case "request-checkout" -> {
                if(admin) throw new IllegalArgumentException("仅学生可申请退宿"); owner(h,false,username); status(h,"CHECKED_IN");
                if(h.get("checkout_requested_at")!=null) throw new IllegalArgumentException("已申请退宿，请等待办理");
                db.update("UPDATE housing SET checkout_requested_at=NOW() WHERE id=?",id);
            }
            case "cancel-checkout" -> {
                if(admin) throw new IllegalArgumentException("仅学生可撤销退宿申请"); owner(h,false,username); status(h,"CHECKED_IN");
                if(h.get("checkout_requested_at")==null) throw new IllegalArgumentException("尚未申请退宿");
                db.update("UPDATE housing SET checkout_requested_at=NULL WHERE id=?",id);
            }
            case "checkout", "force-checkout" -> {
                if(!admin) throw new IllegalArgumentException("仅管理员可办理退宿"); status(h,"CHECKED_IN");
                if(action.equals("checkout") && h.get("checkout_requested_at")==null) throw new IllegalArgumentException("学生尚未申请退宿");
                if(action.equals("force-checkout") && h.get("checkout_requested_at")!=null) throw new IllegalArgumentException("学生已申请退宿，请使用办理退宿");
                long bid=((Number)h.get("bed_id")).longValue(); var bed=locked("bed",bid); status(bed,"OCCUPIED");
                // 在同一事务中更新入住记录和床位：任何一步失败都会回滚。
                db.update("UPDATE housing SET status='CHECKED_OUT',check_out_at=NOW() WHERE id=?",id);
                db.update("UPDATE bed SET status='FREE' WHERE id=?",bid);
            }
            default -> throw new IllegalArgumentException("操作无效");
        }
    }
    public Map<String,Object> statistics() {
        Map<String,Object> out=new HashMap<>();
        out.put("dorms",db.queryForList("SELECT d.building,d.room_no,COUNT(b.id) AS total, COALESCE(SUM(CASE WHEN b.status='OCCUPIED' THEN 1 ELSE 0 END),0) AS occupied FROM dormitory d LEFT JOIN bed b ON b.dormitory_id=d.id GROUP BY d.id,d.building,d.room_no ORDER BY d.building,d.room_no"));
        out.put("beds",db.queryForMap("SELECT COUNT(*) AS total,COALESCE(SUM(status='OCCUPIED'),0) AS occupied FROM bed"));
        out.put("repairs",db.queryForMap("SELECT COUNT(*) AS total,COALESCE(SUM(status IN ('COMPLETED','CONFIRMED')),0) AS done FROM repair"));
        out.put("repairStates",db.queryForList("SELECT status,COUNT(*) AS total FROM repair GROUP BY status ORDER BY status"));
        return out;
    }
}
