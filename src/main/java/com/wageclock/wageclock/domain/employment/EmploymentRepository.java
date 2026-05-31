package com.wageclock.wageclock.domain.employment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface EmploymentRepository extends JpaRepository<Employment,Long> {
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM Employment e WHERE e.employer.id = :employerId AND e.worker.id = :workerId")
    boolean existsByEmployerIdAndWorkerId(@Param("employerId") Long employerId, @Param("workerId") Long workerId);

}
