package com.example.springb.service;

import com.example.springb.entity.DataviewStats;
import com.example.springb.mapper.DataviewStatsMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DataviewService {
    
    @Resource
    private DataviewStatsMapper dataviewStatsMapper;
    
    /**
     * 获取风机缺陷类型统计数据（柱状图）
     */
    public Map<String, Object> getDefectTypeStats() {
        List<Map<String, Object>> stats = dataviewStatsMapper.selectDefectTypeStats();

        // 定义风机缺陷类型映射
        // Damage(损伤), Dirt(污垢)
        Map<String, String> typeMapping = new HashMap<>();
        typeMapping.put("normal", "正常");
        typeMapping.put("Damage", "损伤");
        typeMapping.put("damage", "损伤");
        typeMapping.put("Dirt", "污垢");
        typeMapping.put("dirt", "污垢");

        List<String> categories = new ArrayList<>();
        List<Integer> values = new ArrayList<>();

        // 初始化默认值
        Map<String, Integer> defaultStats = new LinkedHashMap<>();
        defaultStats.put("损伤", 0);
        defaultStats.put("污垢", 0);
        defaultStats.put("正常", 0);

        // 填充实际数据（只保留定义的类别）
        Set<String> validCategories = new HashSet<>(Arrays.asList("损伤", "污垢", "正常"));
        for (Map<String, Object> stat : stats) {
            String key = (String) stat.get("stat_key");
            String cnName = typeMapping.getOrDefault(key, key);
            // 只保留当前定义的类别，跳过其他类别
            if (!validCategories.contains(cnName)) {
                continue;
            }
            Integer count = ((Number) stat.get("stat_count")).intValue();
            defaultStats.put(cnName, count);
        }
        
        for (Map.Entry<String, Integer> entry : defaultStats.entrySet()) {
            categories.add(entry.getKey());
            values.add(entry.getValue());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("categories", categories);
        result.put("values", values);
        return result;
    }
    
    /**
     * 获取用户预测占比（饼图）
     */
    public List<Map<String, Object>> getUserPredictionStats() {
        List<Map<String, Object>> stats = dataviewStatsMapper.selectUserPredictionStats();
        List<Map<String, Object>> result = new ArrayList<>();
        
        int total = stats.stream().mapToInt(s -> ((Number) s.get("count")).intValue()).sum();
        
        for (Map<String, Object> stat : stats) {
            Map<String, Object> item = new HashMap<>();
            String userName = (String) stat.get("userName");
            Integer count = ((Number) stat.get("count")).intValue();
            double percentage = total > 0 ? Math.round(count * 100.0 / total * 10) / 10.0 : 0;
            
            item.put("userName", userName);
            item.put("count", count);
            item.put("percentage", percentage);
            result.add(item);
        }
        
        return result;
    }
    
    /**
     * 获取用户置信度统计（雷达图）
     */
    public Map<String, Object> getUserConfidenceStats() {
        List<Map<String, Object>> stats = dataviewStatsMapper.selectUserConfidenceStats();
        
        List<Map<String, Object>> indicators = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        
        for (Map<String, Object> stat : stats) {
            String userName = (String) stat.get("userName");
            Double avgConfidence = ((Number) stat.get("avgConfidence")).doubleValue() * 100;
            
            Map<String, Object> indicator = new HashMap<>();
            indicator.put("name", userName);
            indicator.put("max", 100);
            indicators.add(indicator);
            
            values.add(Math.round(avgConfidence * 100) / 100.0);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("indicators", indicators);
        result.put("values", values);
        return result;
    }
    
    /**
     * 获取日预测趋势（曲线图）
     */
    public Map<String, Object> getDailyTrend(int days) {
        List<Map<String, Object>> stats = dataviewStatsMapper.selectDailyTrend(days);
        
        List<String> dates = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        
        // 填充最近N天数据
        Calendar cal = Calendar.getInstance();
        for (int i = days - 1; i >= 0; i--) {
            Calendar tempCal = (Calendar) cal.clone();
            tempCal.add(Calendar.DATE, -i);
            String dateStr = String.format("%02d-%02d", 
                tempCal.get(Calendar.MONTH) + 1,
                tempCal.get(Calendar.DAY_OF_MONTH));
            dates.add(dateStr);
            counts.add(0);
        }
        
        // 填充实际数据
        for (Map<String, Object> stat : stats) {
            java.sql.Date sqlDate = (java.sql.Date) stat.get("date");
            Calendar statCal = Calendar.getInstance();
            statCal.setTime(sqlDate);
            String dateStr = String.format("%02d-%02d",
                statCal.get(Calendar.MONTH) + 1,
                statCal.get(Calendar.DAY_OF_MONTH));
            
            int index = dates.indexOf(dateStr);
            if (index >= 0) {
                counts.set(index, ((Number) stat.get("count")).intValue());
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("counts", counts);
        return result;
    }
}
