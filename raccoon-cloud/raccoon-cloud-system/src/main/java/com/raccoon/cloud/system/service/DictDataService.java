package com.raccoon.cloud.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.raccoon.cloud.system.model.DictData;
import com.raccoon.cloud.system.model.dto.DictDataQueryRequest;
import com.raccoon.cloud.system.model.dto.DictDataRequest;

import java.util.List;

public interface DictDataService {

    IPage<DictData> page(DictDataQueryRequest query);

    DictData getById(Long id);

    List<DictData> getByTypeCode(String dictTypeCode);

    void add(DictDataRequest request);

    void update(DictDataRequest request);

    void delete(Long id);

    boolean checkValueUnique(String dictTypeCode, String dictValue, Long excludeId);

    boolean checkLabelUnique(String dictTypeCode, String dictLabel, Long excludeId);

    long countByTypeCode(String dictTypeCode);
}
