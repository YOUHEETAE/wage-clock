package com.wageclock.wageclock.domain.ewatransfer;

import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EwaTransferRepository extends JpaRepository<EwaTransfer, Long> {
    Optional<EwaTransfer> findByMessageNo(String messageNo);

    @Query("SELECT e FROM EwaTransfer e " +
            "JOIN FETCH e.ewaRequest er " +
            "JOIN FETCH er.payPeriod pp " +
            "JOIN FETCH pp.employment emp " +
            "JOIN FETCH emp.worker " +
            "WHERE e.id = :id")
    Optional<EwaTransfer> findByIdWithWorker(@Param("id") Long id);


    // 조회 대상은 전문번호만 쓰므로 연관관계를 함께 가져오지 않는다
    List<EwaTransfer> findByStatusIn(List<EwaTransfer.EwaTransferStatus> status);

    boolean existsByEwaRequest_PayPeriodAndStatusNotIn(PayPeriod payPeriod, List<EwaTransfer.EwaTransferStatus> statuses);
}
