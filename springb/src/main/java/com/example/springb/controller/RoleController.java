package com.example.springb.controller;

import com.example.springb.annotation.RequirePermission;
import com.example.springb.common.Result;
import com.example.springb.entity.SysRole;
import com.example.springb.service.SysRoleService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/role")
@RequirePermission("system:config")
public class RoleController {

    @Resource
    private SysRoleService roleService;

    @GetMapping("/list")
    public Result list(SysRole role) {
        return Result.success(roleService.selectAll(role));
    }

    @GetMapping("/permissions")
    public Result permissions() {
        return Result.success(roleService.selectPermissions());
    }

    @GetMapping("/{id}")
    public Result detail(@PathVariable Integer id) {
        return Result.success(roleService.selectDetail(id));
    }

    @PostMapping("/add")
    public Result add(@RequestBody SysRole role) {
        roleService.add(role);
        return Result.success();
    }

    @PutMapping("/update")
    public Result update(@RequestBody SysRole role) {
        roleService.update(role);
        return Result.success();
    }

    @DeleteMapping("/delete/{id}")
    public Result delete(@PathVariable Integer id) {
        roleService.deleteById(id);
        return Result.success();
    }

    @PostMapping("/assign")
    @SuppressWarnings("unchecked")
    public Result assign(@RequestBody Map<String, Object> params) {
        Integer roleId = params.get("roleId") == null ? null : Integer.valueOf(String.valueOf(params.get("roleId")));
        List<Integer> permissionIds = (List<Integer>) params.get("permissionIds");
        roleService.assignPermissions(roleId, permissionIds);
        return Result.success();
    }
}
