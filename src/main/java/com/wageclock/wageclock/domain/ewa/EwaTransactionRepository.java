package com.wageclock.wageclock.domain.ewa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EwaTransactionRepository extends JpaRepository<EwaTransaction, Long> {
}
