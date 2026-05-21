package com.raccoon.cloud.agent.web;

import com.raccoon.cloud.agent.dto.MinioHealthVO;
import com.raccoon.cloud.agent.dto.MinioObjectVO;
import com.raccoon.cloud.agent.dto.MinioUploadVO;
import com.raccoon.cloud.agent.service.MinioStorageService;
import com.raccoon.common.result.HxResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MinIO 对象存储测试接口。
 */
@RestController
@RequestMapping("/minio")
@RequiredArgsConstructor
public class MinioStorageController {

    private final MinioStorageService minioStorageService;

    @GetMapping("/health")
    public HxResult<MinioHealthVO> health() {
        return HxResult.success(minioStorageService.health());
    }

    @PostMapping("/upload")
    public HxResult<MinioUploadVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prefix", required = false) String prefix) throws Exception {
        return HxResult.success(minioStorageService.upload(file, prefix));
    }

    @GetMapping("/objects")
    public HxResult<List<MinioObjectVO>> listObjects(
            @RequestParam(value = "prefix", required = false) String prefix,
            @RequestParam(value = "limit", defaultValue = "50") int limit) throws Exception {
        return HxResult.success(minioStorageService.listObjects(prefix, limit));
    }

    @GetMapping("/presign")
    public HxResult<Map<String, String>> presign(@RequestParam("objectKey") String objectKey) throws Exception {
        Map<String, String> data = new HashMap<>(2);
        data.put("objectKey", objectKey);
        data.put("url", minioStorageService.presign(objectKey));
        return HxResult.success(data);
    }

    @DeleteMapping("/object")
    public HxResult<Void> delete(@RequestParam("objectKey") String objectKey) throws Exception {
        minioStorageService.delete(objectKey);
        return HxResult.success();
    }
}
