package cn.edu.dorm;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    public record Field(String name, String label, String type, int max, boolean required, Map<String,String> options) {}
    public record Entity(String title, String table, List<String> columns, List<Field> fields) {}
    public record Page(List<Map<String,Object>> rows, int page, int pages, long total) {}
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final SessionService sessions;
    public AdminService(JdbcTemplate db, PasswordEncoder passwords, SessionService sessions) { this.db = db; this.passwords = passwords; this.sessions = sessions; }
    private Field f(String name, String label, int max) { return new Field(name,label,"text",max,true,Map.of()); }
    private Field select(String name, String label, Map<String,String> options) { return new Field(name,label,"select",0,true,options); }
    private Map<String,String> options(String sql, Object... args) {
        Map<String,String> result = new LinkedHashMap<>();
        db.query(sql, rs -> { result.put(rs.getString(1), rs.getString(2)); }, args);
        return result;
    }
    public Entity entity(String key) {
        return switch (key) {
            case "users" -> new Entity("账号", "`user`", List.of("id","username","role"), List.of(f("username","用户名",50),
                new Field("password","密码","password",100,false,Map.of()),
                select("role","角色",new LinkedHashMap<>(Map.of("ADMIN","管理员","STUDENT","学生")))));
            case "students" -> new Entity("学生", "student", List.of("id","user_id","student_no","name"), List.of(
                select("user_id","学生账号",options("SELECT id, username FROM `user` WHERE role='STUDENT' ORDER BY id")),
                f("student_no","学号",30), f("name","姓名",50)));
            case "dormitories" -> new Entity("宿舍", "dormitory", List.of("id","building","room_no"), List.of(f("building","楼栋",50),f("room_no","房间号",20)));
            case "beds" -> new Entity("床位", "bed", List.of("id","dormitory_id","bed_no","status"), List.of(
                select("dormitory_id","宿舍",options("SELECT id, CONCAT(building,' ',room_no) FROM dormitory ORDER BY building, room_no")),
                f("bed_no","床位号",20)));
            default -> throw new IllegalArgumentException("不存在此管理模块");
        };
    }
    // 弹窗选项：新增学生时只列出尚未关联学生的账号，编辑时保留本行正在使用的账号。
    public Entity form(String key, Long editingId) {
        Entity entity = entity(key);
        if (!key.equals("students")) return entity;
        Map<String,String> free = editingId == null
            ? options("SELECT id, username FROM `user` u WHERE role='STUDENT' AND NOT EXISTS (SELECT 1 FROM student s WHERE s.user_id=u.id) ORDER BY id")
            : options("SELECT id, username FROM `user` u WHERE role='STUDENT' AND (NOT EXISTS (SELECT 1 FROM student s WHERE s.user_id=u.id) OR u.id=(SELECT user_id FROM student WHERE id=?)) ORDER BY id", editingId);
        List<Field> fields = entity.fields().stream()
            .map(field -> field.name().equals("user_id") ? select(field.name(), field.label(), free) : field)
            .toList();
        return new Entity(entity.title(), entity.table(), entity.columns(), fields);
    }
    public Page list(String key, int requested) {
        Entity entity = entity(key);
        long total = db.queryForObject("SELECT COUNT(*) FROM " + entity.table(),Long.class);
        int pages = (int)Math.max(1,(total+9)/10), page = Math.max(1,Math.min(requested,pages));
        List<Map<String,Object>> rows = db.queryForList("SELECT " + String.join(",", entity.columns()) + " FROM " + entity.table() + " ORDER BY id DESC LIMIT 10 OFFSET ?",(page-1)*10);
        for (Map<String,Object> row: rows) {
            for (Field field: entity.fields()) if (field.type().equals("select") && row.get(field.name()) != null) {
                String val = row.get(field.name()).toString(); row.put(field.name()+"_label",field.options().getOrDefault(val,val));
            }
        }
        return new Page(rows,page,pages,total);
    }
    public Map<String,Object> find(String key, long id) {
        Entity e=entity(key);
        return db.query("SELECT " + String.join(",", e.columns()) + " FROM " + e.table()+" WHERE id=?", rs -> {
            if (!rs.next()) throw new IllegalArgumentException("记录不存在");
            var row = new LinkedHashMap<String,Object>();
            for (String column:e.columns()) row.put(column,rs.getObject(column));
            return row;
        },id);
    }
    @Transactional
    public void save(String key, Long id, Map<String,String> input, String currentUser) {
        if (id != null) find(key,id);
        Entity e=form(key,id);
        if (key.equals("students")) {
            String account=input.getOrDefault("user_id","").trim();
            if (account.matches("\\d+")) {
                List<Long> owners=db.queryForList("SELECT id FROM student WHERE user_id=?",Long.class,Long.valueOf(account));
                if (!owners.isEmpty() && !owners.get(0).equals(id)) throw new IllegalArgumentException("该账号已关联学生记录");
            }
        }
        Map<String,Object> values = new LinkedHashMap<>();
        boolean passwordChanged = false;
        for (Field field:e.fields()) {
            String value=input.getOrDefault(field.name(),"").trim();
            if (field.name().equals("password")) {
                if (id == null && value.isEmpty()) throw new IllegalArgumentException("新账号必须设置密码");
                if (!value.isEmpty()) {
                    if (value.length()<6 || value.length()>100) throw new IllegalArgumentException("密码长度须为6至100个字符");
                    values.put("password_hash",passwords.encode(value));
                    passwordChanged = true;
                }
                continue;
            }
            if (field.required() && value.isEmpty()) throw new IllegalArgumentException(field.label()+"不能为空");
            if (field.max()>0 && value.length()>field.max()) throw new IllegalArgumentException(field.label()+"过长");
            if (field.type().equals("select") && !field.options().containsKey(value)) throw new IllegalArgumentException(field.label()+"无效");
            values.put(field.name(),value);
        }
        if (key.equals("users") && id != null) {
            Map<String,Object> old=db.queryForMap("SELECT username,role,password_hash FROM `user` WHERE id=? FOR UPDATE",id);
            if (old.get("username").equals(currentUser) && (!old.get("username").equals(values.get("username")) || !old.get("role").equals(values.get("role"))))
                throw new IllegalArgumentException("不能修改当前登录账号的用户名或角色");
            if (!"STUDENT".equals(values.get("role")) && db.queryForObject("SELECT COUNT(*) FROM student WHERE user_id=?",Integer.class,id)>0)
                throw new IllegalArgumentException("该账号已关联学生，不能更改为管理员");
            if (old.get("role").equals("ADMIN") && !"ADMIN".equals(values.get("role")) && db.queryForObject("SELECT COUNT(*) FROM `user` WHERE role='ADMIN'",Integer.class)<=1)
                throw new IllegalArgumentException("至少保留一个管理员账号");
            if (passwordChanged && old.get("username").equals(currentUser)) {
                String oldPassword=input.getOrDefault("oldPassword","").trim();
                if (oldPassword.isEmpty()) throw new IllegalArgumentException("修改本人密码需填写原密码");
                if (!passwords.matches(oldPassword,Objects.toString(old.get("password_hash"),""))) throw new IllegalArgumentException("原密码不正确");
            }
        }
        if (key.equals("beds") && id != null && "OCCUPIED".equals(db.queryForObject("SELECT status FROM bed WHERE id=? FOR UPDATE",String.class,id)))
            throw new IllegalArgumentException("已占用的床位不能修改");
        if (id == null) {
            String columns=String.join(",",values.keySet());
            String marks=String.join(",",Collections.nCopies(values.size(),"?"));
            db.update("INSERT INTO "+e.table()+" ("+columns+") VALUES ("+marks+")",values.values().toArray());
        } else {
            String sets=String.join(",",values.keySet().stream().map(k -> k+"=?").toList());
            List<Object> args=new ArrayList<>(values.values()); args.add(id);
            db.update("UPDATE "+e.table()+" SET "+sets+" WHERE id=?",args.toArray());
        }
        if (key.equals("users") && passwordChanged) sessions.expireOthers(Objects.toString(values.get("username"),""));
    }
    @Transactional
    public void delete(String key, long id, String currentUser) {
        Entity e=entity(key);
        if (key.equals("users")) {
            Map<String,Object> u=db.queryForMap("SELECT username,role FROM `user` WHERE id=? FOR UPDATE",id);
            if (u.get("username").equals(currentUser)) throw new IllegalArgumentException("不能删除当前登录账号");
            if (u.get("role").equals("ADMIN") && db.queryForObject("SELECT COUNT(*) FROM `user` WHERE role='ADMIN'",Integer.class)<=1)
                throw new IllegalArgumentException("至少保留一个管理员账号");
            if (db.queryForObject("SELECT COUNT(*) FROM student WHERE user_id=?",Integer.class,id)>0)
                throw new IllegalArgumentException("该账号已关联学生档案，请先删除对应学生记录");
        }
        if (key.equals("students")) {
            if (db.queryForObject("SELECT COUNT(*) FROM repair WHERE student_id=?",Integer.class,id)>0)
                throw new IllegalArgumentException("该学生存在报修记录，无法删除");
            if (db.queryForObject("SELECT COUNT(*) FROM housing WHERE student_id=?",Integer.class,id)>0)
                throw new IllegalArgumentException("该学生存在入住记录，无法删除");
        }
        if (key.equals("dormitories") && db.queryForObject("SELECT COUNT(*) FROM bed WHERE dormitory_id=?",Integer.class,id)>0)
            throw new IllegalArgumentException("该宿舍下仍有床位，请先删除床位");
        if (key.equals("beds") && "OCCUPIED".equals(db.queryForObject("SELECT status FROM bed WHERE id=? FOR UPDATE",String.class,id)))
            throw new IllegalArgumentException("已占用的床位不能删除");
        if (key.equals("beds") && db.queryForObject("SELECT COUNT(*) FROM housing WHERE bed_id=?",Integer.class,id)>0)
            throw new IllegalArgumentException("该床位存在入住记录，无法删除");
        if (db.update("DELETE FROM "+e.table()+" WHERE id=?",id)==0) throw new IllegalArgumentException("记录不存在");
    }
}
