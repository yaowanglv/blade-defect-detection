package com.example.springb.service;

import com.example.springb.entity.VideoDetect;
import com.example.springb.mapper.VideoDetectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VideoDetectService {

    @Resource
    private VideoDetectMapper videoDetectMapper;

    public void add(VideoDetect videoDetect) {
        videoDetectMapper.insert(videoDetect);
    }

    public void update(VideoDetect videoDetect) {
        videoDetectMapper.updateById(videoDetect);
    }

    public void deleteById(Integer id) {
        videoDetectMapper.deleteById(id);
    }

    public void deleteBatch(List<VideoDetect> list) {
        for (VideoDetect vd : list) {
            this.deleteById(vd.getId());
        }
    }

    public List<VideoDetect> selectAll(VideoDetect videoDetect) {
        return videoDetectMapper.selectAll(videoDetect);
    }

    public PageInfo<VideoDetect> selectPage(Integer pageNum, Integer pageSize, VideoDetect videoDetect) {
        PageHelper.startPage(pageNum, pageSize);
        List<VideoDetect> list = videoDetectMapper.selectPage(videoDetect);
        return PageInfo.of(list);
    }

    public VideoDetect selectById(Integer id) {
        return videoDetectMapper.selectById(id);
    }
}
