package com.raccoon.cloud.agent.web;

import com.raccoon.cloud.agent.dto.Neo4jCypherRequest;
import com.raccoon.cloud.agent.dto.Neo4jHealthVO;
import com.raccoon.cloud.agent.dto.Neo4jStatsVO;
import com.raccoon.cloud.agent.service.Neo4jGraphService;
import com.raccoon.common.result.HxResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Neo4j 图数据库连接与只读查询接口。
 */
@RestController
@RequestMapping("/neo4j")
@RequiredArgsConstructor
public class Neo4jGraphController {

    private final Neo4jGraphService neo4jGraphService;

    @GetMapping("/health")
    public HxResult<Neo4jHealthVO> health() {
        return HxResult.success(neo4jGraphService.health());
    }

    @GetMapping("/stats")
    public HxResult<Neo4jStatsVO> stats() {
        return HxResult.success(neo4jGraphService.stats());
    }

    @PostMapping("/cypher/read")
    public HxResult<List<Map<String, Object>>> readCypher(@Valid @RequestBody Neo4jCypherRequest request) {
        return HxResult.success(neo4jGraphService.runReadQuery(request.getCypher()));
    }
}
