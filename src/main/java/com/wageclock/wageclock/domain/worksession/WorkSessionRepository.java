package com.wageclock.wageclock.domain.worksession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface WorkSessionRepository extends JpaRepository<WorkSession, Long> {
    boolean  existsByEmploymentIdAndStatus(Long employmentId, WorkSession.WorkSessionStatus status);
}
