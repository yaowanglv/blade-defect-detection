package com.example.springb.controller;

import com.example.springb.common.Result;
import com.example.springb.entity.Admin;
import com.example.springb.service.AdminService;
import com.example.springb.service.AuthService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

@RestController
public class WebController {

    @Resource
    AdminService adminService;

    @Resource
    AuthService authService;



    @GetMapping("/")
    public Result miao(){
        return Result.success("2077");
    }


//    @PostMapping("/login")
//    public Result login(@RequestBody Admin admin) {
//        adminService.login(admin);
//        if ("ADMIN".equals(account.getRole())) {
//            dbAccount = adminService.login(account);
//        } else if ("USER".equals(account.getRole())) {
//            dbAccount = userService.login(account);
//        } else {
//            throw new CustomerException("非法请求");
//        }
//        return Result.success(dbAccount);
//    }

    @PostMapping("/login")
    public Result login(@RequestBody Admin admin, HttpServletRequest request) {
        Map<String, Object> result = authService.login(admin.getUsername(), admin.getPassword(), request.getRemoteAddr());
        return Result.success(result);
    }





}
