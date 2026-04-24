package com.scnu.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;

@Configuration
public class MinioConfig {
    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${minio.accessKey:${minio.accesskey:minioadmin}}")
    private String accessKey;

    @Value("${minio.secretKey:${minio.secretkey:minioadmin}}")
    private String secretKey;

    @Value("${minio.bucketName:${minio.bucketname:media}}")
    private String bucketName;

    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            boolean found = client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                System.out.println("[MinIO] Bucket not found, creating: " + bucketName);
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }

            String policyJson = "{\n" +
                    "  \"Version\": \"2012-10-17\",\n" +
                    "  \"Statement\": [\n" +
                    "    {\n" +
                    "      \"Effect\": \"Allow\",\n" +
                    "      \"Principal\": {\n" +
                    "        \"AWS\": [\"*\"]\n" +
                    "      },\n" +
                    "      \"Action\": [\"s3:GetObject\"],\n" +
                    "      \"Resource\": [\"arn:aws:s3:::" + bucketName + "/*\"]\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";

            client.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(policyJson)
                            .build()
            );

            System.out.println("[MinIO] Ready. Bucket policy set to public read: " + bucketName);
            return client;
        } catch (Exception e) {
            throw new RuntimeException("MinIO init failed", e);
        }
    }
}
