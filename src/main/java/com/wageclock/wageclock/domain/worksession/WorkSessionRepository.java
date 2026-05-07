package com.wageclock.wageclock.domain.worksession;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface WorkSessionRepository extends JpaRepository<WorkSession, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WorkSession w WHERE w.id = :id")
    Optional<WorkSession> findByIdWithLock(@Param("id") Long id);
    boolean  existsByEmploymentIdAndStatus(Long employmentId, WorkSession.WorkSessionStatus status);
}
