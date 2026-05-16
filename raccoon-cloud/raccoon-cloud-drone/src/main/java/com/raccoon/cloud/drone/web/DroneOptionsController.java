package com.raccoon.cloud.drone.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.raccoon.cloud.drone.entity.UavInfo;
import com.raccoon.cloud.drone.entity.UavMap;
import com.raccoon.cloud.drone.mapper.UavInfoMapper;
import com.raccoon.cloud.drone.mapper.UavMapMapper;
import com.raccoon.common.result.HxResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/options")
@RequiredArgsConstructor
public class DroneOptionsController {

    private final UavMapMapper uavMapMapper;
    private final UavInfoMapper uavInfoMapper;

    @GetMapping("/maps")
    public HxResult<List<UavMap>> maps() {
        return HxResult.success(
                uavMapMapper.selectList(new LambdaQueryWrapper<UavMap>().orderByAsc(UavMap::getId))
        );
    }

    @GetMapping("/uavs")
    public HxResult<List<UavInfo>> uavs(
            @RequestParam(value = "mapId", required = false) Long mapId
    ) {
        LambdaQueryWrapper<UavInfo> w = new LambdaQueryWrapper<>();
        w.eq(UavInfo::getStatus, 1);
        w.eq(mapId != null, UavInfo::getMapId, mapId);
        w.orderByAsc(UavInfo::getId);
        return HxResult.success(uavInfoMapper.selectList(w));
    }
}
