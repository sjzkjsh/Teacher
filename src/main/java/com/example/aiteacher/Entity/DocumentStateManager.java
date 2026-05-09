package com.example.aiteacher.Entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
@AllArgsConstructor
public class DocumentStateManager {
    private final File stateFile;
    private final ObjectMapper objectMapper;
    private Map<String, FileState> stateMap;

    //存放状态
    public DocumentStateManager(File stateFile, ObjectMapper objectMapper) {
        this.stateFile = stateFile;
        this.objectMapper = objectMapper;
        this.stateMap = new ConcurrentHashMap<>();
    }

    // 加载历史状态
    public void loadState() throws IOException {
        if (stateFile.exists()) {
            stateMap = objectMapper.readValue(
                stateFile,
                new TypeReference<Map<String, FileState>>() {}
            );
        }
    }

    // 获取指定文件的已记录状态
    public FileState getState(String absolutePath) {
        return stateMap.get(absolutePath);
    }

    // 更新文件状态
    public void updateState(String absolutePath, FileState state) {
        stateMap.put(absolutePath, state);
    }

    // 移除状态记录
    public void removeState(String absolutePath) {
        stateMap.remove(absolutePath);
    }

    // 持久化状态到磁盘
    public void saveState() throws IOException {
        objectMapper.writeValue(stateFile, stateMap);
    }

    // 文件状态模型
    @Data
    public static class FileState {
        private long lastModified;
        private long size;

        public FileState() {}
        public FileState(long lastModified, long size) {
            this.lastModified = lastModified;
            this.size = size;
        }
    }
}