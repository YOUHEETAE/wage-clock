package com.wageclock.wageclock.domain.settlement;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BulkSettlementRepository extends JpaRepository<BulkSettlement, Long> {
    Optional<BulkSettlement> findByPortOnePaymentId(String portOnePaymentId);

    List<BulkSettlement> findByStatus(BulkSettlement.BulkSettlementStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM BulkSettlement b WHERE b.portOnePaymentId = :portOnePaymentId")
    Optional<BulkSettlement> findByPortOnePaymentIdWithLock(@Param("portOnePaymentId") String portOnePaymentId);

    // 이체 도중 프로세스가 죽으면 TRANSFERRING인 채로 남는데, 어느 스케줄러도 그 상태를 훑지 않는다.
    // 일정 시간 이상 머문 건을 찾아 회수하기 위한 조회다.
    @Query("SELECT b FROM BulkSettlement b WHERE b.status = 'TRANSFERRING' AND b.updatedAt < :threshold")
    List<BulkSettlement> findStaleTransferring(@Param("threshold") LocalDateTime threshold);

}
