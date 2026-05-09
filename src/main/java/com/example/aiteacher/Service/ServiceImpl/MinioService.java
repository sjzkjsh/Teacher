package com.example.aiteacher.Service.ServiceImpl;

import io.minio.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

@Service
public class MinioService {
    @Autowired
    private MinioClient minioClient;

    private static final int DEFAULT_EXPIRY_MINUTES = 10;

    // 确保桶存在
    private void ensureBucket(String bucketName) throws Exception {
        boolean found = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucketName).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    // ========== 1. 上传字节数组（核心新增） ==========
    public String uploadBytes(String bucketName, String objectName, byte[] data, String contentType)
            throws Exception {
        ensureBucket(bucketName);
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .stream(new ByteArrayInputStream(data), data.length, -1)
                        .contentType(contentType)
                        .build()
        );
        return objectName; // 返回对象名，方便存数据库
    }

    // ========== 2. 生成预签名下载 URL（核心新增） ==========
    public String generatePresignedUrl(String bucketName, String objectName)
            throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(DEFAULT_EXPIRY_MINUTES, TimeUnit.MINUTES)
                        .build()
        );
    }

    // 保留你原来的本地文件上传（如果需要兼容旧逻辑）
    public void uploadFile(String bucketName, String objectName, String filePath) throws Exception {
        ensureBucket(bucketName);
        minioClient.uploadObject(
                UploadObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .filename(filePath)
                        .build());
    }

    // 保留本地文件下载（通常用于后台分析，不是前端导出）
    public void downloadFile(String bucketName, String objectName, String savePath) throws Exception {
        minioClient.downloadObject(
                DownloadObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .filename(savePath)
                        .build());
    }
}