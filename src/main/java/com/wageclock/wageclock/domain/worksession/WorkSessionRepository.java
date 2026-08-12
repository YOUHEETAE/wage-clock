package com.wageclock.wageclock.domain.worksession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface WorkSessionRepository extends JpaRepository<WorkSession, Long> {
    boolean existsByEmploymentIdAndStatus(Long employmentId, WorkSession.WorkSessionStatus status);
    Optional<WorkSession> findByEmploymentIdAndStatusNot(Long employmentId, WorkSession.WorkSessionStatus status);
    boolean existsByEmploymentIdAndStatusNot(Long employmentId, WorkSession.WorkSessionStatus status);
}
