package com.adlin.orin.modules.runner.repository;

import com.adlin.orin.modules.runner.entity.RunnerCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RunnerCredentialRepository extends JpaRepository<RunnerCredential, String> {

    Optional<RunnerCredential> findByCredentialId(String credentialId);

    List<RunnerCredential> findByRunnerId(String runnerId);

    Optional<RunnerCredential> findFirstByRunnerIdAndStatusOrderByCreatedAtDesc(String runnerId,
                                                                              RunnerCredential.Status status);
}
