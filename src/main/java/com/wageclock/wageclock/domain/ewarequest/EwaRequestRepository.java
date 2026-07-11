package com.wageclock.wageclock.domain.ewarequest;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EwaRequestRepository extends JpaRepository<EwaRequest, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EwaRequest e WHERE e.id = :id")
    Optional<EwaRequest> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT new com.wageclock.wageclock.domain.ewarequest.PendingEwaResponse(e.id, w.name, e.requestedAmount, e.createdAt) " +
            "FROM EwaRequest e " +
            "JOIN e.payPeriod pp " +
            "JOIN pp.employment em " +
            "JOIN em.worker w " +
            "WHERE em.workplace.id = :workplaceId AND em.employer.id = :employerId AND e.status = 'PENDING'")
    List<PendingEwaResponse> findPendingByWorkplace(@Param("workplaceId") Long workplaceId, @Param("employerId") Long employerId);

}
