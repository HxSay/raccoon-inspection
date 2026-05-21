package com.raccoon.cloud.agent.service;

import com.raccoon.cloud.agent.config.MinioProperties;
import com.raccoon.cloud.agent.dto.MinioHealthVO;
import com.raccoon.cloud.agent.dto.MinioObjectVO;
import com.raccoon.cloud.agent.dto.MinioUploadVO;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.http.Method;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    @PostConstruct
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getBucketName()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucketName()).build());
                log.info("created minio bucket: {}", properties.getBucketName());
            }
        } catch (Exception e) {
            log.warn("ensure minio bucket failed: {}", e.getMessage());
        }
    }

    public MinioHealthVO health() {
        MinioHealthVO vo = new MinioHealthVO();
        vo.setEndpoint(properties.getEndpoint());
        vo.setBucketName(properties.getBucketName());
        vo.setUrlExpireDays(properties.getUrlExpireDays());
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getBucketName()).build());
            vo.setBucketExists(exists);
            vo.setReachable(true);
            vo.setMessage(exists ? "MinIO 连接正常，Bucket 已就绪" : "MinIO 已连接，Bucket 不存在");
        } catch (Exception e) {
            vo.setReachable(false);
            vo.setBucketExists(false);
            vo.setMessage("无法连接 MinIO: " + e.getMessage());
            log.warn("minio health check failed: {}", e.getMessage());
        }
        return vo;
    }

    public MinioUploadVO upload(MultipartFile file, String prefix) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        ensureBucketReady();
        String objectKey = buildObjectKey(file.getOriginalFilename(), prefix);
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucketName())
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                            .build());
        }
        MinioUploadVO vo = new MinioUploadVO();
        vo.setObjectKey(objectKey);
        vo.setBucketName(properties.getBucketName());
        vo.setSize(file.getSize());
        vo.setContentType(file.getContentType());
        vo.setPresignedUrl(presign(objectKey));
        return vo;
    }

    public List<MinioObjectVO> listObjects(String prefix, int limit) throws Exception {
        ensureBucketReady();
        int max = limit > 0 ? Math.min(limit, 200) : 50;
        String normalizedPrefix = normalizePrefix(prefix);
        List<MinioObjectVO> list = new ArrayList<>();
        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(properties.getBucketName())
                        .prefix(normalizedPrefix)
                        .recursive(true)
                        .build());
        for (Result<Item> result : results) {
            Item item = result.get();
            if (item.isDir()) {
                continue;
            }
            MinioObjectVO vo = new MinioObjectVO();
            vo.setObjectKey(item.objectName());
            vo.setSize(item.size());
            vo.setEtag(item.etag());
            if (item.lastModified() != null) {
                vo.setLastModified(item.lastModified().toInstant());
            }
            list.add(vo);
            if (list.size() >= max) {
                break;
            }
        }
        return list;
    }

    public String presign(String objectKey) throws Exception {
        ensureBucketReady();
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        int expireSeconds = Math.max(properties.getUrlExpireDays(), 1) * 24 * 3600;
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(properties.getBucketName())
                        .object(objectKey.trim())
                        .expiry(expireSeconds, TimeUnit.SECONDS)
                        .build());
    }

    public void delete(String objectKey) throws Exception {
        ensureBucketReady();
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(properties.getBucketName())
                        .object(objectKey.trim())
                        .build());
    }

    private void ensureBucketReady() throws Exception {
        MinioHealthVO health = health();
        if (!health.isReachable()) {
            throw new IllegalStateException(health.getMessage());
        }
        if (!health.isBucketExists()) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucketName()).build());
        }
    }

    private String buildObjectKey(String originalFilename, String prefix) {
        String safeName = StringUtils.hasText(originalFilename)
                ? originalFilename.replaceAll("[^a-zA-Z0-9._\\-]", "_")
                : "file";
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String folder = StringUtils.hasText(prefix) ? normalizePrefix(prefix) : "test/";
        return folder + date + "/" + UUID.randomUUID() + "-" + safeName;
    }

    private String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        String p = prefix.trim().replace("\\", "/");
        if (!p.endsWith("/")) {
            p = p + "/";
        }
        return p.startsWith("/") ? p.substring(1) : p;
    }
}
