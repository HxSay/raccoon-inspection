package com.raccoon.cloud.agent.ai.milvus.web;

import com.raccoon.cloud.agent.ai.milvus.dto.MilvusTestDocumentVO;
import com.raccoon.cloud.agent.ai.milvus.dto.MilvusTestGetByIdRequest;
import com.raccoon.cloud.agent.ai.milvus.dto.MilvusTestInsertRequest;
import com.raccoon.cloud.agent.ai.milvus.dto.MilvusTestInsertResultVO;
import com.raccoon.cloud.agent.ai.milvus.dto.MilvusTestSearchRequest;
import com.raccoon.cloud.agent.ai.milvus.service.MilvusTestService;
import com.raccoon.common.result.HxResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Milvus 向量库调试接口（个人 RAG 调试用，无权限校验）。
 * <p>
 * 网关访问示例：POST /api/agent/ai/milvus/test/insert
 */
@RestController
@RequestMapping("/ai/milvus/test")
@RequiredArgsConstructor
public class MilvusTestController {

    private final MilvusTestService milvusTestService;

    @PostMapping("/insert")
    public HxResult<MilvusTestInsertResultVO> insert(@Valid @RequestBody MilvusTestInsertRequest request) {
        return HxResult.success(milvusTestService.insert(request));
    }

    @PostMapping("/search")
    public HxResult<List<MilvusTestDocumentVO>> search(@Valid @RequestBody MilvusTestSearchRequest request) {
        return HxResult.success(milvusTestService.search(request));
    }

    @PostMapping("/getById")
    public HxResult<MilvusTestDocumentVO> getById(@Valid @RequestBody MilvusTestGetByIdRequest request) {
        return HxResult.success(milvusTestService.getById(request));
    }
}
