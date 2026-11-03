package cn.edu.dorm;

import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final AdminService admin;
    private final BusinessService business;
    public ApiController(AdminService admin, BusinessService business) { this.admin=admin; this.business=business; }
    private boolean isAdmin(Authentication a) { return a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_ADMIN")); }
    private Long id(Object value) {
        if(value == null || value.toString().isBlank()) return null;
        try { return Long.valueOf(value.toString()); }
        catch(NumberFormatException e) { throw new IllegalArgumentException("编号无效"); }
    }
    @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }
    @GetMapping("/session") Map<String,Object> session(Authentication a) { return Map.of("username",a.getName(),"admin",isAdmin(a)); }
    @GetMapping("/profile") Map<String,Object> profile(Authentication a) {
        if (isAdmin(a)) throw new IllegalArgumentException("管理员请在账号管理中维护资料");
        return business.profile(a.getName());
    }
    @PutMapping("/profile") void updateProfile(@RequestBody Map<String,Object> data, Authentication a) {
        if (isAdmin(a)) throw new IllegalArgumentException("管理员请在账号管理中维护资料");
        business.updateProfile(a.getName(), Objects.toString(data.get("name"),""), Objects.toString(data.get("student_no"),""), data.get("password")==null ? null : data.get("password").toString());
    }
    @GetMapping("/admin/statistics") Map<String,Object> statistics() { return business.statistics(); }
    @GetMapping("/admin/{entity:users|students|dormitories|beds}") Map<String,Object> adminList(@PathVariable String entity,@RequestParam(defaultValue="1") int page) {
        return Map.of("meta",admin.entity(entity),"data",admin.list(entity,page));
    }
    @GetMapping("/admin/{entity:users|students|dormitories|beds}/{id}") Map<String,Object> adminDetail(@PathVariable String entity,@PathVariable long id) { return admin.find(entity,id); }
    @PostMapping("/admin/{entity:users|students|dormitories|beds}") void adminCreate(@PathVariable String entity,@RequestBody Map<String,String> data,Authentication a) { admin.save(entity,null,data,a.getName()); }
    @PutMapping("/admin/{entity:users|students|dormitories|beds}/{id}") void adminUpdate(@PathVariable String entity,@PathVariable long id,@RequestBody Map<String,String> data,Authentication a) { admin.save(entity,id,data,a.getName()); }
    @DeleteMapping("/admin/{entity:users|students|dormitories|beds}/{id}") void adminDelete(@PathVariable String entity,@PathVariable long id,Authentication a) { admin.delete(entity,id,a.getName()); }
    @GetMapping("/repairs") BusinessService.Page repairs(@RequestParam(defaultValue="1") int page,@RequestParam(required=false) String keyword,@RequestParam(required=false) String dorm,@RequestParam(required=false) String status,Authentication a) {
        return business.repairs(isAdmin(a),a.getName(),page,keyword,dorm,status);
    }
    @GetMapping("/repairs/{id}") Map<String,Object> repairDetail(@PathVariable long id,Authentication a) { return business.repairDetail(id,isAdmin(a),a.getName()); }
    @PostMapping("/repairs") void repairCreate(@RequestBody Map<String,Object> data,Authentication a) { business.createRepair(isAdmin(a),a.getName(),id(data.get("studentId")),Objects.toString(data.get("description"),"")); }
    @PutMapping("/repairs/{id}") void repairUpdate(@PathVariable long id,@RequestBody Map<String,Object> data,Authentication a) { business.editRepair(id,isAdmin(a),a.getName(),Objects.toString(data.get("description"),"")); }
    @DeleteMapping("/repairs/{id}") void repairDelete(@PathVariable long id,Authentication a) { business.deleteRepair(id,isAdmin(a),a.getName()); }
    @PostMapping("/repairs/{id}/{action:accept|complete|confirm}") void repairAction(@PathVariable long id,@PathVariable String action,@RequestBody(required=false) Map<String,Object> data,Authentication a) {
        business.repairAction(id,action,isAdmin(a),a.getName(),data==null?null:Objects.toString(data.get("evaluation"),""));
    }
    @GetMapping("/housing") BusinessService.Page housing(@RequestParam(defaultValue="1") int page,Authentication a) { return business.list("housing",isAdmin(a),a.getName(),page); }
    @PostMapping("/housing") void housingCreate(@RequestBody(required=false) Map<String,Object> data,Authentication a) { business.createHousing(isAdmin(a),a.getName(),data==null?null:id(data.get("studentId"))); }
    @DeleteMapping("/housing/{id}") void housingDelete(@PathVariable long id,Authentication a) { business.deleteHousing(id,isAdmin(a),a.getName()); }
    @PostMapping("/housing/{id}/{action:assign|checkin|request-checkout|checkout}") void housingAction(@PathVariable long id,@PathVariable String action,@RequestBody(required=false) Map<String,Object> data,Authentication a) {
        business.housingAction(id,action,isAdmin(a),a.getName(),data==null?null:id(data.get("bedId")));
    }
    @GetMapping("/admin/options/students") List<Map<String,Object>> students() { return business.students(); }
    @GetMapping("/admin/options/free-beds") List<Map<String,Object>> beds() { return business.freeBeds(); }
    @ExceptionHandler({IllegalArgumentException.class,DataAccessException.class})
    ResponseEntity<Map<String,String>> error(Exception ex) {
        String message=ex instanceof DataIntegrityViolationException?"保存失败：数据重复、关联记录存在或不满足约束":ex instanceof DataAccessException?"数据操作失败，请检查关联记录及输入":ex.getMessage();
        return ResponseEntity.badRequest().body(Map.of("message",message));
    }
}
