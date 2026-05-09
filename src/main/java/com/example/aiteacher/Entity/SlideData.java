package com.example.aiteacher.Entity;

import lombok.Data;

import java.util.List;

@Data
public class SlideData {
    private int page;
    private String title;
    private List<String> points;
    private String notes;          // 讲师备注（可选）
    private String imageKeyword;   // 可选：图片关键词


}