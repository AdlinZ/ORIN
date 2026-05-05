package com.adlin.orin.modules.notification.repository;

import com.adlin.orin.modules.notification.entity.SystemMessageUserState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SystemMessageUserStateRepository extends JpaRepository<SystemMessageUserState, Long> {

    Optional<SystemMessageUserState> findByMessageIdAndUserId(Long messageId, String userId);

    List<SystemMessageUserState> findByUserIdAndMessageIdIn(String userId, Collection<Long> messageIds);
}
