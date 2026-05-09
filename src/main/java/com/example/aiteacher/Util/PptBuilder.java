package com.example.aiteacher.Util;

import com.example.aiteacher.Entity.CoursewareOutline;
import com.example.aiteacher.Entity.SlideData;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * PPT 课件生成器
 * 优先使用模板填充，模板不存在时自动从零创建
 */
public class PptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PptBuilder.class);

    // 从零创建时的配色
    private static final Color TITLE_COLOR = new Color(44, 62, 80);
    private static final Color SUBTITLE_COLOR = new Color(52, 152, 219);
    private static final Color BODY_COLOR = new Color(52, 73, 94);
    private static final Color ACCENT_COLOR = new Color(52, 152, 219);
    private static final double TITLE_SIZE = 36.0;
    private static final double SUBTITLE_SIZE = 20.0;
    private static final double BODY_SIZE = 16.0;

    /**
     * 生成 PPT
     */
    public static String createPptFromTemplate(CoursewareOutline outline,
                                               String templatePath,
                                               String outputDir) throws IOException {

        XMLSlideShow ppt;

        // 尝试通过 ClassLoader 加载模板（比 ClassPathResource.exists() 更可靠）
        InputStream templateStream = PptBuilder.class.getClassLoader().getResourceAsStream(templatePath);
        if (templateStream != null) {
            log.info("找到模板文件: {}，使用模板生成", templatePath);
            try {
                ppt = buildFromTemplate(templateStream, outline);
            } catch (Exception e) {
                log.warn("模板加载失败，回退到自动创建: {}", e.getMessage());
                ppt = buildFromScratch(outline);
            }
        } else {
            log.warn("模板文件不存在: {}，自动创建 PPT（请确认文件在 src/main/resources/templates/ 下）", templatePath);
            ppt = buildFromScratch(outline);
        }

        // 保存文件
        String filename = safeName(outline.getTitle()) + ".pptx";
        File dir = new File(outputDir);
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, filename);
        try (FileOutputStream fos = new FileOutputStream(file)) {
            ppt.write(fos);
        }
        ppt.close();
        return file.getAbsolutePath();
    }

    // =====================================================================
    //  模式一：基于模板填充
    // =====================================================================

    private static XMLSlideShow buildFromTemplate(InputStream templateStream,
                                                  CoursewareOutline outline) throws IOException {
        XMLSlideShow ppt = new XMLSlideShow(templateStream);

        // 清空模板中已有的幻灯片（保留母版和版式）
        while (ppt.getSlides().size() > 0) {
            ppt.removeSlide(0);
        }

        // 先探测默认版式是否含有可用的文本形状
        // 如果默认版式是空白的（没有文本框），fillSlide 会静默跳过，导致白板
        XSLFSlide testSlide = ppt.createSlide();
        boolean hasTextShapes = false;
        for (XSLFShape shape : testSlide.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                hasTextShapes = true;
                break;
            }
        }
        // 移除探测用的幻灯片
        ppt.removeSlide(ppt.getSlides().size() - 1);

        if (!hasTextShapes) {
            log.warn("模板默认版式没有文本形状，回退到自动创建模式");
            ppt.close();
            return buildFromScratch(outline);
        }

        // 封面（使用默认版式创建，然后填充文本框）
        XSLFSlide coverSlide = ppt.createSlide();
        fillSlide(coverSlide, outline.getTitle(), outline.getSubtitle(), true);

        // 内容页
        if (outline.getSlides() != null) {
            for (SlideData sd : outline.getSlides()) {
                XSLFSlide slide = ppt.createSlide();
                fillSlide(slide, sd.getTitle(), sd.getPoints(), false);
            }
        }

        return ppt;
    }

    /**
     * 填充单页幻灯片
     * 遍历幻灯片中所有文本形状，按顺序填入标题和内容
     */
    private static void fillSlide(XSLFSlide slide, String title, Object content, boolean isCover) {
        // 收集所有文本形状
        List<XSLFTextShape> textShapes = new ArrayList<>();
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                textShapes.add((XSLFTextShape) shape);
            }
        }
        if (textShapes.isEmpty()) return;

        // 第一个文本框 = 标题
        writeText(textShapes.get(0), title);

        // 第二个文本框 = 内容（如果有的话）
        if (textShapes.size() >= 2) {
            if (isCover && content instanceof String) {
                writeText(textShapes.get(1), (String) content);
            } else if (!isCover && content instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> points = (List<String>) content;
                writeBullets(textShapes.get(1), points);
            }
        }
    }

    // =====================================================================
    //  模式二：从零创建
    // =====================================================================

    private static XMLSlideShow buildFromScratch(CoursewareOutline outline) {
        XMLSlideShow ppt = new XMLSlideShow();
        ppt.setPageSize(new Dimension(12192000, 6858000)); // 16:9

        // 封面
        XSLFSlide cover = ppt.createSlide();
        makeCover(cover, outline.getTitle(), outline.getSubtitle());

        // 内容页
        if (outline.getSlides() != null) {
            for (SlideData sd : outline.getSlides()) {
                XSLFSlide slide = ppt.createSlide();
                makeContent(slide, sd.getTitle(), sd.getPoints(), sd.getNotes());
            }
        }

        return ppt;
    }

    private static void makeCover(XSLFSlide slide, String title, String subtitle) {
        // 左侧装饰条
        XSLFTextBox bar = slide.createTextBox();
        bar.setAnchor(new Rectangle(0, 0, 60000, 6858000));

        // 标题
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(900000, 2200000, 10000000, 1200000));
        XSLFTextRun r1 = titleBox.addNewTextParagraph().addNewTextRun();
        r1.setText(title != null ? title : "课件标题");
        r1.setFontSize(TITLE_SIZE);
        r1.setBold(true);
        r1.setFontColor(TITLE_COLOR);

        // 副标题
        if (subtitle != null && !subtitle.isEmpty()) {
            XSLFTextBox subBox = slide.createTextBox();
            subBox.setAnchor(new Rectangle(900000, 3500000, 10000000, 600000));
            XSLFTextRun r2 = subBox.addNewTextParagraph().addNewTextRun();
            r2.setText(subtitle);
            r2.setFontSize(SUBTITLE_SIZE);
            r2.setFontColor(SUBTITLE_COLOR);
        }
    }

    private static void makeContent(XSLFSlide slide, String title, List<String> points, String notes) {
        // 标题
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(600000, 350000, 10800000, 800000));
        XSLFTextRun tr = titleBox.addNewTextParagraph().addNewTextRun();
        tr.setText(title != null ? title : "");
        tr.setFontSize(TITLE_SIZE);
        tr.setBold(true);
        tr.setFontColor(TITLE_COLOR);

        // 要点
        if (points != null && !points.isEmpty()) {
            XSLFTextBox body = slide.createTextBox();
            body.setAnchor(new Rectangle(600000, 1350000, 10800000, 4800000));
            for (String point : points) {
                XSLFTextParagraph pp = body.addNewTextParagraph();
                XSLFTextRun dot = pp.addNewTextRun();
                dot.setText("•  ");
                dot.setFontSize(BODY_SIZE);
                dot.setBold(true);
                dot.setFontColor(ACCENT_COLOR);
                XSLFTextRun content = pp.addNewTextRun();
                content.setText(point);
                content.setFontSize(BODY_SIZE);
                content.setFontColor(BODY_COLOR);
            }
        }

        // 讲师备注（放在页面底部小字区域）
        if (notes != null && !notes.isEmpty()) {
            XSLFTextBox noteBox = slide.createTextBox();
            noteBox.setAnchor(new Rectangle(600000, 6200000, 10800000, 500000));
            XSLFTextRun nr = noteBox.addNewTextParagraph().addNewTextRun();
            nr.setText("备注: " + notes);
            nr.setFontSize(10.0);
            nr.setFontColor(new Color(150, 150, 150));
        }
    }

    // =====================================================================
    //  通用工具
    // =====================================================================

    private static void writeText(XSLFTextShape shape, String text) {
        shape.clearText();
        if (text == null || text.isEmpty()) return;
        XSLFTextRun run = shape.addNewTextParagraph().addNewTextRun();
        run.setText(text);
        run.setFontSize(TITLE_SIZE);
        run.setBold(true);
        run.setFontColor(TITLE_COLOR);
    }

    private static void writeBullets(XSLFTextShape shape, List<String> points) {
        shape.clearText();
        if (points == null || points.isEmpty()) return;
        for (String point : points) {
            XSLFTextParagraph para = shape.addNewTextParagraph();
            XSLFTextRun dot = para.addNewTextRun();
            dot.setText("•  ");
            dot.setFontSize(BODY_SIZE);
            dot.setBold(true);
            dot.setFontColor(ACCENT_COLOR);
            XSLFTextRun content = para.addNewTextRun();
            content.setText(point);
            content.setFontSize(BODY_SIZE);
            content.setFontColor(BODY_COLOR);
        }
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) return "课件";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
