package com.raccoon.cloud.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.raccoon.cloud.system.model.DictType;
import com.raccoon.cloud.system.model.dto.DictTypeQueryRequest;
import com.raccoon.cloud.system.model.dto.DictTypeRequest;

import java.util.List;

public interface DictTypeService {

    IPage<DictType> page(DictTypeQueryRequest query);

    DictType getById(Long id);

    DictType getByCode(String dictTypeCode);

    void add(DictTypeRequest request);

    void update(DictTypeRequest request);

    void delete(Long id);

    boolean checkCodeUnique(String dictTypeCode, Long excludeId);

    List<DictType> listAll();
}
