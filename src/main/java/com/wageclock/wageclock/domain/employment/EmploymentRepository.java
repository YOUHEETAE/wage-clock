package com.wageclock.wageclock.domain.employment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface EmploymentRepository extends JpaRepository<Employment,Long> {
    boolean existsByEmployerIdAndWorkerId(Long employerId, Long workerId);
}
