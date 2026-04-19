package com.raccoon.cloud.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.raccoon.cloud.system.mapper.DictTypeMapper;
import com.raccoon.cloud.system.model.DictType;
import com.raccoon.cloud.system.model.dto.DictTypeQueryRequest;
import com.raccoon.cloud.system.model.dto.DictTypeRequest;
import com.raccoon.cloud.system.service.DictTypeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DictTypeServiceImpl implements DictTypeService {

    private static final String CACHE_KEY_PREFIX = "dict:type:";

    @Autowired
    private DictTypeMapper dictTypeMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public IPage<DictType> page(DictTypeQueryRequest query) {
        QueryWrapper<DictType> wrapper = new QueryWrapper<>();
        if (query.getDictTypeCode() != null) {
            wrapper.like("dict_type_code", query.getDictTypeCode());
        }
        if (query.getDictTypeName() != null) {
            wrapper.like("dict_type_name", query.getDictTypeName());
        }
        if (query.getStatus() != null) {
            wrapper.eq("status", query.getStatus());
        }
        wrapper.eq("is_deleted", 0);
        wrapper.orderByDesc("sort", "create_time");

        Page<DictType> page = new Page<>(query.getPage(), query.getSize());
        return dictTypeMapper.selectPage(page, wrapper);
    }

    @Override
    public DictType getById(Long id) {
        return dictTypeMapper.selectById(id);
    }

    @Override
    public DictType getByCode(String dictTypeCode) {
        String cacheKey = CACHE_KEY_PREFIX + dictTypeCode;
        DictType cached = (DictType) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        QueryWrapper<DictType> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_type_code", dictTypeCode);
        wrapper.eq("is_deleted", 0);
        DictType dictType = dictTypeMapper.selectOne(wrapper);

        if (dictType != null) {
            redisTemplate.opsForValue().set(cacheKey, dictType, 1, TimeUnit.HOURS);
        } else {
            // 缓存空值，避免缓存穿透
            redisTemplate.opsForValue().set(cacheKey, "", 5, TimeUnit.MINUTES);
        }
        return dictType;
    }

    @Override
    public void add(DictTypeRequest request) {
        DictType dictType = new DictType();
        BeanUtils.copyProperties(request, dictType);
        dictType.setTenantId(0L);
        dictType.setIsDeleted(0);
        dictTypeMapper.insert(dictType);
    }

    @Override
    public void update(DictTypeRequest request) {
        DictType dictType = dictTypeMapper.selectById(request.getId());
        if (dictType == null) {
            throw new RuntimeException("字典类型不存在");
        }
        BeanUtils.copyProperties(request, dictType);
        dictTypeMapper.updateById(dictType);

        String cacheKey = CACHE_KEY_PREFIX + dictType.getDictTypeCode();
        redisTemplate.delete(cacheKey);
    }

    @Override
    public void delete(Long id) {
        DictType dictType = dictTypeMapper.selectById(id);
        if (dictType == null) {
            throw new RuntimeException("字典类型不存在");
        }
        dictType.setIsDeleted(1);
        dictTypeMapper.updateById(dictType);

        String cacheKey = CACHE_KEY_PREFIX + dictType.getDictTypeCode();
        redisTemplate.delete(cacheKey);
    }

    @Override
    public boolean checkCodeUnique(String dictTypeCode, Long excludeId) {
        QueryWrapper<DictType> wrapper = new QueryWrapper<>();
        wrapper.eq("dict_type_code", dictTypeCode);
        wrapper.eq("is_deleted", 0);
        if (excludeId != null) {
            wrapper.ne("id", excludeId);
        }
        return dictTypeMapper.selectCount(wrapper) == 0;
    }

    @Override
    public List<DictType> listAll() {
        QueryWrapper<DictType> wrapper = new QueryWrapper<>();
        wrapper.eq("is_deleted", 0);
        wrapper.eq("status", 1);
        wrapper.orderByDesc("sort", "create_time");
        return dictTypeMapper.selectList(wrapper);
    }
}
