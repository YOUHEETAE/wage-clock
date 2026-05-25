package com.wageclock.wageclock.domain.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EwaOutBoxEventRepository extends JpaRepository<EwaOutBoxEvent,Long> {
    List<EwaOutBoxEvent> findByStatus(EwaOutBoxEvent.OutBoxStatus status);
    Optional<EwaOutBoxEvent> findByPortOnePaymentId(String portOnePaymentId);
}
