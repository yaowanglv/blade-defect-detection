package com.example.springb.mapper;

import com.example.springb.entity.SysRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysRoleMapper {

    @Select("SELECT role_code FROM sys_role r " +
            "INNER JOIN sys_user_role ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND r.status = 1")
    List<String> selectRoleCodesByUserId(@Param("userId") Integer userId);

    @Select("SELECT id FROM sys_role WHERE role_code = #{roleCode} LIMIT 1")
    Integer selectIdByCode(@Param("roleCode") String roleCode);

    List<SysRole> selectAll(SysRole role);

    @Select("SELECT * FROM sys_role WHERE id = #{id}")
    SysRole selectById(@Param("id") Integer id);

    @Insert("INSERT INTO sys_role(role_code, role_name, description, status) " +
            "VALUES(#{roleCode}, #{roleName}, #{description}, COALESCE(#{status}, 1))")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SysRole role);

    @Update("UPDATE sys_role SET role_code=#{roleCode}, role_name=#{roleName}, description=#{description}, status=#{status} WHERE id=#{id}")
    void updateById(SysRole role);

    @Delete("DELETE FROM sys_role WHERE id = #{id}")
    void deleteById(@Param("id") Integer id);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    void deleteRolePermissions(@Param("roleId") Integer roleId);

    @Insert("INSERT IGNORE INTO sys_role_permission(role_id, permission_id) VALUES(#{roleId}, #{permissionId})")
    void insertRolePermission(@Param("roleId") Integer roleId, @Param("permissionId") Integer permissionId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    void deleteUserRoles(@Param("userId") Integer userId);

    @Delete("DELETE FROM sys_user_role WHERE role_id = #{roleId}")
    void deleteUserRolesByRoleId(@Param("roleId") Integer roleId);

    @Insert("INSERT IGNORE INTO sys_user_role(user_id, role_id) VALUES(#{userId}, #{roleId})")
    void insertUserRole(@Param("userId") Integer userId, @Param("roleId") Integer roleId);
}
