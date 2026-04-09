package com.adlin.orin.modules.monitor.repository;

import com.adlin.orin.modules.monitor.entity.ServerInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServerInfoRepository extends JpaRepository<ServerInfo, Long> {

    /**
     * 根据服务器 ID 查询服务器信息
     */
    Optional<ServerInfo> findByServerId(String serverId);

    /**
     * 检查服务器是否存在
     */
    boolean existsByServerId(String serverId);

    /**
     * 根据多个服务器 ID 查询服务器信息
     */
    List<ServerInfo> findByServerIdIn(List<String> serverIds);
}
