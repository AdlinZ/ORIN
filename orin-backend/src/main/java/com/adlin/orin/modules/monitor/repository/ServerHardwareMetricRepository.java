package com.adlin.orin.modules.monitor.repository;

import com.adlin.orin.modules.monitor.entity.ServerHardwareMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServerHardwareMetricRepository extends JpaRepository<ServerHardwareMetric, Long> {

    /**
     * 查询一段时间内的硬件监控数据
     */
    List<ServerHardwareMetric> findByTimestampBetweenOrderByTimestampAsc(Long startTime, Long endTime);

    /**
     * 查询一段时间内的硬件监控数据（分页）
     */
    Page<ServerHardwareMetric> findByTimestampBetween(Long startTime, Long endTime, Pageable pageable);

    /**
     * 获取最新的 N 条记录
     */
    List<ServerHardwareMetric> findTop100ByOrderByTimestampDesc();

    /**
     * 获取最新的 1 条记录
     */
    Optional<ServerHardwareMetric> findTopByOrderByTimestampDesc();

    /**
     * 查询指定时间之后的记录
     */
    List<ServerHardwareMetric> findByTimestampAfterOrderByTimestampAsc(Long timestamp);

    /**
     * 统计总记录数
     */
    long count();

    /**
     * 统计指定时间之后的记录数
     */
    long countByTimestampAfter(Long timestamp);

    /**
     * 删除指定时间之前的记录
     */
    void deleteByTimestampBefore(Long timestamp);

    /**
     * 获取最早记录的 timestamp
     */
    @Query("SELECT MIN(m.timestamp) FROM ServerHardwareMetric m")
    Optional<Long> findMinTimestamp();

    /**
     * 获取最近 1 小时的记录
     */
    @Query("SELECT m FROM ServerHardwareMetric m WHERE m.timestamp >= ?1 ORDER BY m.timestamp ASC")
    List<ServerHardwareMetric> findLastHour(Long oneHourAgo);

    /**
     * 查询指定节点在一段时间内的硬件监控数据
     */
    List<ServerHardwareMetric> findByServerIdAndTimestampBetweenOrderByTimestampAsc(String serverId, Long startTime, Long endTime);

    /**
     * 查询指定节点在一段时间内的硬件监控数据（分页）
     */
    Page<ServerHardwareMetric> findByServerIdAndTimestampBetween(String serverId, Long startTime, Long endTime, Pageable pageable);

    /**
     * 查询指定节点在指定时间之后的记录
     */
    List<ServerHardwareMetric> findByServerIdAndTimestampAfterOrderByTimestampAsc(String serverId, Long timestamp);

    /**
     * 获取所有不同的服务器节点
     */
    @Query("SELECT DISTINCT new map(m.serverId as id, m.serverName as name) FROM ServerHardwareMetric m WHERE m.serverId IS NOT NULL")
    List<java.util.Map<String, Object>> findDistinctServerNodes();
}
