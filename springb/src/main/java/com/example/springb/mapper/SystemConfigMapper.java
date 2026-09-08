package com.example.springb.mapper;

import com.example.springb.entity.SystemConfig;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SystemConfigMapper {

    @Select("SELECT * FROM system_config WHERE config_group = #{configGroup} ORDER BY sort_order, id")
    List<SystemConfig> selectByGroup(@Param("configGroup") String configGroup);

    @Select("SELECT * FROM system_config ORDER BY config_group, sort_order, id")
    List<SystemConfig> selectAll();

    @Select("SELECT * FROM system_config WHERE config_key = #{configKey} AND config_group = #{configGroup} LIMIT 1")
    SystemConfig selectByKeyAndGroup(@Param("configKey") String configKey, @Param("configGroup") String configGroup);

    @Insert("INSERT INTO system_config (config_key, config_value, config_group, config_label, default_value, sort_order, create_time, update_time) " +
            "VALUES (#{configKey}, #{configValue}, #{configGroup}, #{configLabel}, #{defaultValue}, #{sortOrder}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SystemConfig config);

    @Update("UPDATE system_config SET config_value = #{configValue}, update_time = NOW() WHERE config_key = #{configKey} AND config_group = #{configGroup}")
    int updateValueByKeyAndGroup(@Param("configKey") String configKey,
                                 @Param("configGroup") String configGroup,
                                 @Param("configValue") String configValue);

    int updateById(SystemConfig config);
}
