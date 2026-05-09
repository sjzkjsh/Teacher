package com.example.aiteacher.Config;
import com.example.aiteacher.Entity.DocumentStateManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.JsonReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component
public class DataLoader {
    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;
    private final String rootPath;
    private final boolean incremental;
    private final File stateFile;

    public DataLoader(VectorStore vectorStore,
                      ObjectMapper objectMapper,
                      @Value("${app.document.root-path:./user-data}") String rootPath,
                      @Value("${app.document.incremental:true}") boolean incremental,
                      @Value("${app.document.state-file:./data-loader-state.json}") String stateFilePath) {
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
        this.rootPath = rootPath;
        this.incremental = incremental;
        this.stateFile = new File(stateFilePath);
    }



    @Async("documentLoadExecutor")
    @PostConstruct
    public void load() {
        try {
            //创建状态管理器并加载历史记录
            DocumentStateManager stateManager = new DocumentStateManager(stateFile, objectMapper);
            if (incremental) {
                stateManager.loadState();
            }
            //检查目录是否存在
            Path rootDir = Paths.get(rootPath);
            if (!Files.exists(rootDir)) {
                Files.createDirectories(rootDir);
                log.warn("外部文档目录不存在，已自动创建: {}", rootDir.toAbsolutePath());
                return;
            }
            //创建分词器，用于拆分文档
            TokenTextSplitter splitter = new TokenTextSplitter();
            List<String> currentFilesOnDisk = new ArrayList<>();

            // 遍历所有文件，并根据状态判断是否需要处理，这个遍历是可以递归的分层的
            Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // 获取文件绝对路径
                    String absolutePath = file.toAbsolutePath().toString();
                    currentFilesOnDisk.add(absolutePath);
                    // 获取文件状态
                    long lastModified = attrs.lastModifiedTime().toMillis();
                    long size = attrs.size();
                    DocumentStateManager.FileState currentState = new DocumentStateManager.FileState(lastModified, size);
                    DocumentStateManager.FileState previousState = stateManager.getState(absolutePath);

                    // 判断是否需要处理：无历史记录，或状态发生变化
                    if (previousState == null ||
                            previousState.getLastModified() != lastModified ||
                            previousState.getSize() != size) {
                        try {

                            //处理变化的文件
                            log.info("处理文件: {}", file.getFileName());
                            List<Document> docs = readDocuments(file.toFile());
                            if (!docs.isEmpty()) {
                                // 分词
                                List<Document> splitDocs = splitter.apply(docs);
                                // 如果之前存在，最好先删除旧数据（此处简化，跳过删除）
                                // 实际生产中可根据元数据中的 source 字段过滤删除
                                vectorStore.add(splitDocs);
                            }
                            // 更新状态
                            stateManager.updateState(absolutePath, currentState);
                        } catch (Exception e) {
                            log.error("处理文件失败: {}", file.getFileName(), e);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            // 如果开启增量，可清理已在磁盘上不存在的文件记录（但不删除向量库中的旧数据）
            if (incremental) {
                // 移除状态文件中已不存在的文件记录（可选）
                Set<String> diskFiles = new HashSet<>(currentFilesOnDisk);
                stateManager.getStateMap().keySet().removeIf(key -> !diskFiles.contains(key));
            }

            stateManager.saveState();
            log.info("文档加载完成，共处理 {} 个文件目录", rootDir.toAbsolutePath());
        } catch (Exception e) {
            log.error("文档加载过程出现异常", e);
        }
    }

    // 根据文件后缀选择对应的 Reader
    private List<Document> readDocuments(File file) throws IOException {
        String filename = file.getName().toLowerCase();

        if (filename.endsWith(".pdf")) {
            return new PagePdfDocumentReader(new FileSystemResource(file),
                    PdfDocumentReaderConfig.builder().withPagesPerDocument(1).build())
                    .read();
        } else if (filename.endsWith(".md") || filename.endsWith(".markdown")) {
            return new MarkdownDocumentReader(new FileSystemResource(file),
                    MarkdownDocumentReaderConfig.builder()
                            .withIncludeCodeBlock(false)
                            .withHorizontalRuleCreateDocument(true)
                            .build())
                    .read();
        } else if (filename.endsWith(".json")) {
            // 根据实际 JSON 结构指定字段
            return new JsonReader(new FileSystemResource(file), "content").read();
        } else {
            return new TikaDocumentReader(new FileSystemResource(file)).read();
        }
    }
}