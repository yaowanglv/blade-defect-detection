package com.example.springb.service;

import com.example.springb.entity.SysPermission;
import com.example.springb.entity.SysRole;
import com.example.springb.exception.CustomerException;
import com.example.springb.mapper.SysPermissionMapper;
import com.example.springb.mapper.SysRoleMapper;
import com.example.springb.security.SecurityUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SysRoleService {

    @Resource
    private SysRoleMapper roleMapper;

    @Resource
    private SysPermissionMapper permissionMapper;

    @Resource
    private SecurityUtils securityUtils;

    public List<SysRole> selectAll(SysRole role) {
        return roleMapper.selectAll(role);
    }

    public List<SysPermission> selectPermissions() {
        return permissionMapper.selectAll();
    }

    public Map<String, Object> selectDetail(Integer roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new CustomerException("400", "角色不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("role", role);
        result.put("permissionIds", permissionMapper.selectPermissionIdsByRoleId(roleId));
        return result;
    }

    public void add(SysRole role) {
        role.setRoleCode(securityUtils.normalizeRoleCode(role.getRoleCode()));
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        roleMapper.insert(role);
    }

    public void update(SysRole role) {
        if (role.getId() == null) {
            throw new CustomerException("400", "角色ID不能为空");
        }
        role.setRoleCode(securityUtils.normalizeRoleCode(role.getRoleCode()));
        if (role.getStatus() == null) {
            role.setStatus(1);
        }
        roleMapper.updateById(role);
    }

    public void deleteById(Integer id) {
        roleMapper.deleteRolePermissions(id);
        roleMapper.deleteUserRolesByRoleId(id);
        roleMapper.deleteById(id);
    }

    @Transactional
    public void assignPermissions(Integer roleId, List<Integer> permissionIds) {
        if (roleId == null) {
            throw new CustomerException("400", "角色ID不能为空");
        }
        roleMapper.deleteRolePermissions(roleId);
        if (permissionIds == null) {
            return;
        }
        for (Integer permissionId : permissionIds) {
            if (permissionId != null) {
                roleMapper.insertRolePermission(roleId, permissionId);
            }
        }
    }
}
