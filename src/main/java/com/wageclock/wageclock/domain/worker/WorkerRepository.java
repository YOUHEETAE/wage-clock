package com.wageclock.wageclock.domain.worker;

import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, Long> {
    Optional<Worker> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsById(@Nonnull Long id);
}
