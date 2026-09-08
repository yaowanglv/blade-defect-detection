package com.example.springb.mapper;

import com.example.springb.entity.Admin;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface AdminMapper {
    List<Admin> selectAll(Admin admin);

    void insert(Admin admin);

    @Select("select  * from  `admin` where username=#{username}")
    Admin selectByUsername(String username);

    @Select("select * from `admin` where id=#{id}")
    Admin selectById(Integer id);

    void updateById(Admin admin);

    @Update("update `admin` set password=#{password} where id=#{id}")
    void updatePasswordById(Admin admin);

    @Update("update `admin` set last_login_time=#{lastLoginTime}, last_login_ip=#{lastLoginIp} where id=#{id}")
    void updateLastLoginInfo(@Param("id") Integer id,
                             @Param("lastLoginTime") String lastLoginTime,
                             @Param("lastLoginIp") String lastLoginIp);

    @Delete("delete from  `admin` where id=#{id}")
    void deleteById(Integer id);
}
