package com.raccoon.cloud.drone.dispatch.cache;

import com.raccoon.cloud.drone.dispatch.constant.DispatchConstants;
import com.raccoon.cloud.drone.dispatch.model.TerminalState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 终端状态缓存抽象层。
 * <p>当前实现采用线程安全的 {@link ConcurrentHashMap} 作为进程内缓存（项目未引入 Redis 依赖），
 * 接口设计与 Redis (spring-data-redis) 完全对齐：仅需替换该类内部实现即可平滑迁移到 Redis，
 * 保持「缓存优先 → DB 兜底」的语义。
 * <p>每条缓存附带写入时间戳，超过 {@link DispatchConstants#TERMINAL_STATE_TTL_SEC} 后视为失效，
 * 由调用方触发回源查询。
 *
 * @author raccoon
 */
@Slf4j
@Component
public class TerminalStateCacheStore {

    /** key=terminalId, value=带写入时戳的状态条目 */
    private final ConcurrentHashMap<Long, Entry> store = new ConcurrentHashMap<>();

    /**
     * 读取缓存。
     *
     * @param terminalId 终端 ID
     * @return 命中且未过期返回状态，否则 {@link Optional#empty()}
     */
    public Optional<TerminalState> get(Long terminalId) {
        if (terminalId == null) {
            return Optional.empty();
        }
        Entry e = store.get(terminalId);
        if (e == null) {
            return Optional.empty();
        }
        if (isExpired(e)) {
            store.remove(terminalId, e);
            return Optional.empty();
        }
        return Optional.of(e.state);
    }

    /**
     * 写入缓存。覆盖既有值。
     *
     * @param state 终端状态
     */
    public void put(TerminalState state) {
        if (state == null || state.getTerminalId() == null) {
            return;
        }
        store.put(state.getTerminalId(), new Entry(state, LocalDateTime.now()));
        log.debug("[dispatch-cache] put terminal={} aggregatedAt={}",
                state.getTerminalId(), state.getAggregatedAt());
    }

    /**
     * 主动失效。
     *
     * @param terminalId 终端 ID
     */
    public void evict(Long terminalId) {
        if (terminalId != null) {
            store.remove(terminalId);
        }
    }

    /**
     * 清空缓存（测试/演练用）。
     */
    public void clear() {
        store.clear();
    }

    /**
     * 缓存大小。
     */
    public int size() {
        return store.size();
    }

    /** 判定 TTL 过期 */
    private boolean isExpired(Entry e) {
        if (e == null || e.writeAt == null) {
            return true;
        }
        return e.writeAt.plusSeconds(DispatchConstants.TERMINAL_STATE_TTL_SEC)
                .isBefore(LocalDateTime.now());
    }

    /**
     * 缓存条目：保存状态及写入时戳。
     */
    private static final class Entry {
        private final TerminalState state;
        private final LocalDateTime writeAt;

        Entry(TerminalState state, LocalDateTime writeAt) {
            this.state = state;
            this.writeAt = writeAt;
        }
    }
}
