package com.example.springb.mapper;

import com.example.springb.entity.Detect;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface DetectMapper {

    List<Detect> selectAll(Detect detect);

    @Insert("INSERT INTO detect (user_id, user_name, original_image_name, " +
            "original_image_url, original_image_size, original_image_format, " +
            "detect_status, ai_status, remark, create_time) VALUES " +
            "(#{userId}, #{userName}, #{originalImageName}, " +
            "#{originalImageUrl}, #{originalImageSize}, #{originalImageFormat}, " +
            "0, 0, #{remark}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Detect detect);

    void updateById(Detect detect);

    Detect selectById(Integer id);

    @Delete("DELETE FROM detect WHERE id = #{id}")
    void deleteById(Integer id);
}
