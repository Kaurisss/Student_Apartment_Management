package cn.edu.dorm;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
    @GetMapping({"/dashboard", "/repairs", "/housing", "/profile", "/students", "/dormitories", "/beds", "/users"})
    public String index() { return "forward:/index.html"; }
}
