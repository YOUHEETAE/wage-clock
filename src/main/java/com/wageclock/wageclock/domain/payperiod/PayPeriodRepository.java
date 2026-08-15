package com.wageclock.wageclock.domain.payperiod;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayPeriodRepository extends JpaRepository<PayPeriod, Long> {
    Optional<PayPeriod> findByEmployment_IdAndStatus(Long employmentId, PayPeriod.PayPeriodStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PayPeriod p WHERE p.employment.id = :employmentId AND p.status = :status")
    Optional<PayPeriod> findByEmploymentAndStatusWithLock(@Param("employmentId") Long employmentId,
                                                          @Param("status") PayPeriod.PayPeriodStatus status);

    // ORDER BY 필수 — 여러 행을 잠그므로 획득 순서가 고정되지 않으면
    // 일괄 정산끼리 서로 반대 순서로 잠가 데드락이 난다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PayPeriod p WHERE p.employment.id IN :employmentIds AND p.employment.employer.id = :employerId AND p.status = 'ACTIVE' ORDER BY p.id")
    List<PayPeriod> findAllByEmploymentIdInAndEmployerIdAndStatusWithLock(
            @Param("employmentIds") List<Long> employmentIds,
            @Param("employerId") Long employerId);

    Optional<PayPeriod> findByEmployment_IdAndStatusIn(Long employmentId, List<PayPeriod.PayPeriodStatus> statuses);
}
