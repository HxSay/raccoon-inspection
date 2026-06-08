package com.raccoon.cloud.drone.closeloop.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

@Slf4j
@Component
@RequiredArgsConstructor
public class CloseLoopSchemaInitializer implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS `task_close_loop_audit` (
                      `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary key',
                      `task_id` VARCHAR(64) NOT NULL COMMENT 'dispatch task id',
                      `work_order_id` BIGINT NULL COMMENT 'cmms work order id',
                      `task_name` VARCHAR(255) NULL COMMENT 'task name',
                      `close_status` VARCHAR(32) NOT NULL COMMENT 'COMPLETED/PARTIAL/MANUAL_REQUIRED/COLLECTING',
                      `completion_rate` DOUBLE NULL COMMENT 'completion rate 0-1',
                      `missed_items_json` MEDIUMTEXT NULL COMMENT 'missed devices/waypoints/data gaps',
                      `report_json` MEDIUMTEXT NULL COMMENT 'inspection report json',
                      `report_summary` VARCHAR(1024) NULL COMMENT 'report summary',
                      `reschedule_task_id` VARCHAR(64) NULL COMMENT 'reinspection task id',
                      `reschedule_count` INT NULL DEFAULT 0 COMMENT 'reinspection count',
                      `close_time` DATETIME NOT NULL COMMENT 'close loop time',
                      PRIMARY KEY (`id`),
                      KEY `idx_task_id` (`task_id`),
                      KEY `idx_close_time` (`close_time`)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='task close loop audit'
                    """);
            log.info("[closeloop-schema] task_close_loop_audit ready");
        } catch (Exception e) {
            log.error("[closeloop-schema] failed to initialize task_close_loop_audit", e);
        }
    }
}
