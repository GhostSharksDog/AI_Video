package com.scnu.server.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.beans.factory.annotation.Value;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;

@Component
public class MinioUtils {
    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucketname:${minio.bucketName:media}}")
    private String bucketName;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endPoint;

    public String uploadFile(MultipartFile file) throws Exception {
        String originalFileName = file.getOriginalFilename();
        String suffix = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            suffix = originalFileName.substring(originalFileName.lastIndexOf("."));
        }
        String objectName = UUID.randomUUID() + suffix;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
        }

        return endPoint + "/" + bucketName + "/" + objectName;
    }
    
    public void removeFile(String fileUrl) {
        try {
            String objectName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
            System.out.println("[MinIO] remove success: " + objectName);
        } catch (Exception e) {
            System.err.println("[MinIO] remove failed: " + e.getMessage());
        }
    }
    
    public String uploadLocalFile(File file) throws Exception {
        String objectName = file.getName();
        try (FileInputStream inputStream = new FileInputStream(file)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.length(), -1)
                            .contentType("video/mp4")
                            .build()
            );
        }
        return endPoint + "/" + bucketName + "/" + objectName;
    }
}
