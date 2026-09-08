package com.example.springb.mapper;

import com.example.springb.entity.VideoDetect;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface VideoDetectMapper {

    List<VideoDetect> selectAll(VideoDetect videoDetect);

    /**
     * 分页查询（轻量级，不包含大字段）
     */
    List<VideoDetect> selectPage(VideoDetect videoDetect);

    @Insert("INSERT INTO video_detect (user_id, user_name, original_video_name, " +
            "original_video_url, original_video_size, original_video_format, " +
            "detect_status, ai_status, create_time) VALUES " +
            "(#{userId}, #{userName}, #{originalVideoName}, " +
            "#{originalVideoUrl}, #{originalVideoSize}, #{originalVideoFormat}, " +
            "0, 0, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(VideoDetect videoDetect);

    void updateById(VideoDetect videoDetect);

    VideoDetect selectById(Integer id);

    @Delete("DELETE FROM video_detect WHERE id = #{id}")
    void deleteById(Integer id);
}
