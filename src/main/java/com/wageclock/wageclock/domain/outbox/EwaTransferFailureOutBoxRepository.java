package com.wageclock.wageclock.domain.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface EwaTransferFailureOutBoxRepository extends JpaRepository <EwaTransferFailureOutBoxEvent, Long> {
    List<EwaTransferFailureOutBoxEvent> findByStatus(EwaTransferFailureOutBoxEvent.EwaTransferFailureOutBoxStatus status);
}
