package com.example.springb.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;

@Mapper
public interface TokenBlacklistMapper {

    @Insert("INSERT INTO sys_token_blacklist(token, expire_time) VALUES(#{jti}, #{expireTime})")
    void insert(@Param("jti") String jti, @Param("expireTime") Date expireTime);

    @Select("SELECT COUNT(*) FROM sys_token_blacklist WHERE token = #{jti} AND expire_time > NOW()")
    int countValidByJti(@Param("jti") String jti);
}
