package com.raccoon.cloud.system.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 自动填充创建时间
        if (metaObject.hasSetter("createTime")) {
            this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        }
        // 自动填充更新时间
        if (metaObject.hasSetter("updateTime")) {
            this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        }
        // 自动填充创建人
        if (metaObject.hasSetter("createBy")) {
            this.strictInsertFill(metaObject, "createBy", String.class, "admin");
        }
        // 自动填充更新人
        if (metaObject.hasSetter("updateBy")) {
            this.strictInsertFill(metaObject, "updateBy", String.class, "admin");
        }
        // 自动填充租户ID
        if (metaObject.hasSetter("tenantId")) {
            this.strictInsertFill(metaObject, "tenantId", Long.class, 0L);
        }
        // 自动填充删除标志
        if (metaObject.hasSetter("isDeleted")) {
            this.strictInsertFill(metaObject, "isDeleted", Integer.class, 0);
        }
        if (metaObject.hasSetter("delFlag")) {
            this.strictInsertFill(metaObject, "delFlag", Integer.class, 0);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 自动填充更新时间
        if (metaObject.hasSetter("updateTime")) {
            this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        }
        // 自动填充更新人
        if (metaObject.hasSetter("updateBy")) {
            this.strictUpdateFill(metaObject, "updateBy", String.class, "admin");
        }
    }
}
