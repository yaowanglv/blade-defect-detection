package com.example.springb.mapper;

import com.example.springb.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysPermissionMapper {

    @Select("SELECT DISTINCT p.permission_code FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id " +
            "INNER JOIN sys_role r ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectPermissionsByUserId(@Param("userId") Integer userId);

    @Select("SELECT * FROM sys_permission ORDER BY id ASC")
    List<SysPermission> selectAll();

    @Select("SELECT p.id FROM sys_permission p " +
            "INNER JOIN sys_role_permission rp ON p.id = rp.permission_id " +
            "WHERE rp.role_id = #{roleId}")
    List<Integer> selectPermissionIdsByRoleId(@Param("roleId") Integer roleId);
}
