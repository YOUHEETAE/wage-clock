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
    Optional<PayPeriod> findByEmploymentIdAndStatus(Long employmentId, PayPeriod.PayPeriodStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PayPeriod p WHERE p.employment.id = :employmentId AND p.status = :status")
    Optional<PayPeriod> findByEmploymentAndStatusWithLock(@Param("employmentId") Long employmentId,
                                                          @Param("status") PayPeriod.PayPeriodStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PayPeriod p WHERE p.employment.id IN :employmentIds AND p.employment.employer.id = :employerId AND p.status = 'ACTIVE'")
    List<PayPeriod> findAllByEmploymentIdInAndEmployerIdAndStatusWithLock(
            @Param("employmentIds") List<Long> employmentIds,
            @Param("employerId") Long employerId);


}
