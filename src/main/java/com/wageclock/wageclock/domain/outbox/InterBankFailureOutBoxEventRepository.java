package com.wageclock.wageclock.domain.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterBankFailureOutBoxEventRepository extends JpaRepository<InterBankFailureOutBoxEvent, Long> {
    List<InterBankFailureOutBoxEvent> findByStatus(InterBankFailureOutBoxEvent.InterBankFailureOutBoxEventStatus status);
}
