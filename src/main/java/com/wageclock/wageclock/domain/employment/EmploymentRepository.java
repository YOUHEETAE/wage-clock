package com.wageclock.wageclock.domain.employment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface EmploymentRepository extends JpaRepository<Employment,Long> {
    boolean existsByWorkplace_IdAndWorker_Id(Long workplaceId, Long workerId);

    List<Employment> findByWorker_Id(Long workerId);

}
