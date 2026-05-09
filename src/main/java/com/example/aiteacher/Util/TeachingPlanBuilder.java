package com.example.aiteacher.Util;

import com.example.aiteacher.Entity.CoursewareOutline;
import com.example.aiteacher.Entity.SlideData;
import org.apache.poi.xwpf.usermodel.*;

import java.io.*;
import java.util.List;

/**
 * Word 教案生成器
 * 根据大模型返回的大纲 JSON 直接生成教案，不需要模板
 */
public class TeachingPlanBuilder {

    /**
     * 生成 Word 教案
     *
     * @param outline   AI 生成的大纲
     * @param outputDir 输出目录
     * @return 生成的文件绝对路径
     */
    public static String createTeachingPlan(CoursewareOutline outline, String outputDir) throws IOException {
        XWPFDocument doc = new XWPFDocument();

        // 1. 标题
        addTitle(doc, outline.getTitle());

        // 2. 基本信息
        addSectionTitle(doc, "一、基本信息");
        addKeyValue(doc, "课件主题", outline.getTitle());
        if (outline.getSubtitle() != null && !outline.getSubtitle().isEmpty()) {
            addKeyValue(doc, "副标题", outline.getSubtitle());
        }
        addKeyValue(doc, "课时安排", (outline.getSlides() != null ? outline.getSlides().size() : 0) + " 课时");

        // 3. 教学目标
        addSectionTitle(doc, "二、教学目标");
        addParagraph(doc, "通过本课程的学习，学生能够：");
        addBullet(doc, "理解 " + outline.getTitle() + " 的核心概念和基本原理");
        addBullet(doc, "掌握相关知识点的实际应用方法");
        addBullet(doc, "能够运用所学知识解决实际问题");

        // 4. 教学内容
        addSectionTitle(doc, "三、教学内容");
        if (outline.getSlides() != null) {
            int i = 1;
            for (SlideData slide : outline.getSlides()) {
                addSlideContent(doc, i, slide);
                i++;
            }
        }

        // 5. 教学总结
        addSectionTitle(doc, "四、教学总结");
        addParagraph(doc, "本课程围绕「" + outline.getTitle() + "」展开，通过系统讲解和案例分析，"
                + "帮助学生全面理解相关知识点。建议学生课后复习要点内容，并结合实际进行练习。");

        // 6. 保存文件
        String filename = PptBuilder.class.getSimpleName().equals("PptBuilder")
                ? safeName(outline.getTitle()) + "-教案.docx"
                : safeName(outline.getTitle()) + "-教案.docx";
        File dir = new File(outputDir);
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            doc.write(fos);
        }
        doc.close();
        return file.getAbsolutePath();
    }

    // ==================== 文档构建方法 ====================

    /** 添加文档标题 */
    private static void addTitle(XWPFDocument doc, String text) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.CENTER);
        para.setSpacingAfter(200);
        XWPFRun run = para.createRun();
        run.setText(text != null ? text : "教学教案");
        run.setBold(true);
        run.setFontSize(22);
        run.setFontFamily("微软雅黑");
    }

    /** 添加一级标题（如"一、教学目标"） */
    private static void addSectionTitle(XWPFDocument doc, String text) {
        XWPFParagraph para = doc.createParagraph();
        para.setSpacingBefore(300);
        para.setSpacingAfter(100);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(true);
        run.setFontSize(14);
        run.setFontFamily("微软雅黑");
    }

    /** 添加键值对行 */
    private static void addKeyValue(XWPFDocument doc, String key, String value) {
        XWPFParagraph para = doc.createParagraph();
        para.setIndentationLeft(400);
        XWPFRun keyRun = para.createRun();
        keyRun.setText(key + "：");
        keyRun.setBold(true);
        keyRun.setFontSize(11);
        keyRun.setFontFamily("宋体");
        XWPFRun valRun = para.createRun();
        valRun.setText(value != null ? value : "");
        valRun.setFontSize(11);
        valRun.setFontFamily("宋体");
    }

    /** 添加正文段落 */
    private static void addParagraph(XWPFDocument doc, String text) {
        XWPFParagraph para = doc.createParagraph();
        para.setIndentationLeft(400);
        para.setSpacingAfter(60);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setFontSize(11);
        run.setFontFamily("宋体");
    }

    /** 添加圆点列表项 */
    private static void addBullet(XWPFDocument doc, String text) {
        XWPFParagraph para = doc.createParagraph();
        para.setIndentationLeft(800);
        XWPFRun run = para.createRun();
        run.setText("•  " + text);
        run.setFontSize(11);
        run.setFontFamily("宋体");
    }

    /** 添加单页课件内容到教案 */
    private static void addSlideContent(XWPFDocument doc, int index, SlideData slide) {
        // 课时标题
        XWPFParagraph titlePara = doc.createParagraph();
        titlePara.setIndentationLeft(400);
        titlePara.setSpacingBefore(150);
        XWPFRun titleRun = titlePara.createRun();
        titleRun.setText(index + ". " + (slide.getTitle() != null ? slide.getTitle() : "第 " + index + " 页"));
        titleRun.setBold(true);
        titleRun.setFontSize(12);
        titleRun.setFontFamily("宋体");

        // 要点列表
        if (slide.getPoints() != null) {
            for (String point : slide.getPoints()) {
                XWPFParagraph pointPara = doc.createParagraph();
                pointPara.setIndentationLeft(800);
                XWPFRun pointRun = pointPara.createRun();
                pointRun.setText("•  " + point);
                pointRun.setFontSize(11);
                pointRun.setFontFamily("宋体");
            }
        }

        // 讲师备注
        if (slide.getNotes() != null && !slide.getNotes().isEmpty()) {
            XWPFParagraph notePara = doc.createParagraph();
            notePara.setIndentationLeft(800);
            notePara.setSpacingBefore(60);
            XWPFRun labelRun = notePara.createRun();
            labelRun.setText("【教学提示】");
            labelRun.setBold(true);
            labelRun.setFontSize(10);
            labelRun.setFontFamily("宋体");
            labelRun.setColor("666666");
            XWPFRun noteRun = notePara.createRun();
            noteRun.setText(slide.getNotes());
            noteRun.setFontSize(10);
            noteRun.setFontFamily("宋体");
            noteRun.setColor("666666");
        }
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) return "教案";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
