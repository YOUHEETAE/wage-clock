package com.wageclock.wageclock.domain.employment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface EmploymentRepository extends JpaRepository<Employment,Long> {
    boolean existsByWorkplace_IdAndWorker_Id(Long workplaceId, Long workerId);

    List<Employment> findByWorker_Id(Long workerId);

    // 출근 중복 진입을 막는 락.
    // WorkSession이나 PayPeriod는 아직 없을 수 있어 잠글 행이 없으므로,
    // 반드시 존재하는 Employment를 기준점으로 삼는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employment e WHERE e.id = :id")
    Optional<Employment> findByIdWithLock(@Param("id") Long id);
}
