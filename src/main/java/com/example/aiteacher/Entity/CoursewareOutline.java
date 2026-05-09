package com.example.aiteacher.Entity;

import lombok.Data;

import java.util.List;

@Data
public class CoursewareOutline {
    private String title;
    private String subtitle;   // 封面副标题
    private List<SlideData> slides;


}