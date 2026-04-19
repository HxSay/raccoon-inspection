package com.raccoon.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.system.model.DictData;
import com.raccoon.cloud.system.model.dto.DictDataQueryRequest;
import com.raccoon.cloud.system.model.dto.DictDataRequest;
import com.raccoon.cloud.system.service.DictDataService;
import com.raccoon.cloud.system.mapper.DictDataMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DictDataServiceImpl implements DictDataService {

    private static final String CACHE_KEY_PREFIX = "dict:data:";

    @Autowired
    private DictDataMapper dictDataMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public IPage<DictData> page(DictDataQueryRequest query) {
        QueryWrapper<DictData> wrapper = new QueryWrapper<>();
        if (query.getDictTypeCode() != null) {
            wrapper.eq("dict_type_code", query.getDictTypeCode());
        }
        if (query.getDictLabel() != null) {
            wrapper.like("dict_label", query.getDictLabel());
        }
        if (query.getStatus() != null) {
            wrapper.eq("status", query.getStatus());
        }
        wrapper.eq("is_deleted", 0);
        wrapper.orderByDesc("sort", "create_time");

        Page<DictData> page = new Page<>(query.getPage(), query.getSize());
        return dictDataMapper.selectPage(page, wrapper);
    }

    @Override
    public DictData getById(Long id) {
        return dictDataMapper.selectById(id);
    }

    @Override
    public List<DictData> getByTypeCode(String dictTypeCode) {
        String cacheKey = CACHE_KEY_PREFIX + dictTypeCode;
        @SuppressWarnings("unchecked")
        List<DictData> cached = (List<DictData>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        QueryWrapper<DictData> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_type_code", dictTypeCode);
        wrapper.eq("is_deleted", 0);
        wrapper.eq("status", 1);
        wrapper.orderByAsc("sort");
        List<DictData> list = dictDataMapper.selectList(wrapper);

        if (!list.isEmpty()) {
            redisTemplate.opsForValue().set(cacheKey, list, 1, TimeUnit.HOURS);
        } else {
            // 缓存空值，避免缓存穿透
            redisTemplate.opsForValue().set(cacheKey, Collections.emptyList(), 5, TimeUnit.MINUTES);
        }
        return list;
    }

    @Override
    public void add(DictDataRequest request) {
        DictData dictData = new DictData();
        BeanUtils.copyProperties(request, dictData);
        dictData.setTenantId(0L);
        dictData.setIsDeleted(0);
        dictDataMapper.insert(dictData);

        clearCache(request.getDictTypeCode());
    }

    @Override
    public void update(DictDataRequest request) {
        DictData dictData = dictDataMapper.selectById(request.getId());
        if (dictData == null) {
            throw new RuntimeException("字典数据不存在");
        }
        BeanUtils.copyProperties(request, dictData);
        dictDataMapper.updateById(dictData);

        clearCache(request.getDictTypeCode());
    }

    @Override
    public void delete(Long id) {
        DictData dictData = dictDataMapper.selectById(id);
        if (dictData == null) {
            throw new RuntimeException("字典数据不存在");
        }
        dictData.setIsDeleted(1);
        dictDataMapper.updateById(dictData);

        clearCache(dictData.getDictTypeCode());
    }

    @Override
    public boolean checkValueUnique(String dictTypeCode, String dictValue, Long excludeId) {
        QueryWrapper<DictData> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_type_code", dictTypeCode);
        wrapper.eq("dict_value", dictValue);
        wrapper.eq("is_deleted", 0);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        return dictDataMapper.selectCount(wrapper) == 0;
    }

    @Override
    public boolean checkLabelUnique(String dictTypeCode, String dictLabel, Long excludeId) {
        QueryWrapper<DictData> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_type_code", dictTypeCode);
        wrapper.eq("dict_label", dictLabel);
        wrapper.eq("is_deleted", 0);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        return dictDataMapper.selectCount(wrapper) == 0;
    }

    @Override
    public long countByTypeCode(String dictTypeCode) {
        QueryWrapper<DictData> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_type_code", dictTypeCode);
        wrapper.eq("is_deleted", 0);
        return dictDataMapper.selectCount(wrapper);
    }

    private void clearCache(String dictTypeCode) {
        String cacheKey = CACHE_KEY_PREFIX + dictTypeCode;
        redisTemplate.delete(cacheKey);
    }
}
