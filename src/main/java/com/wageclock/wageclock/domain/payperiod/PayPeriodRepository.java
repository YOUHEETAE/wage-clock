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

    // 금액 누계(totalEarnedAmount·totalEwaAmount)를 갱신하는 모든 경로가 이 락을 거친다.
    // 퇴근·EWA 요청·거절·이체 실패 확정이 전부 읽고-고쳐-쓰기라, 락 없이 겹치면
    // 나중에 커밋한 쪽이 앞선 변경을 덮어쓴다.
    // 연관관계 체이닝(session.getPayPeriod())으로 꺼내면 락이 걸리지 않으므로 반드시 이 메서드로 조회한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PayPeriod p WHERE p.id = :id")
    Optional<PayPeriod> findByIdWithLock(@Param("id") Long payPeriodId);
}
