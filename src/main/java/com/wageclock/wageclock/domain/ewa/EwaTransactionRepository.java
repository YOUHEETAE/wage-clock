package com.wageclock.wageclock.domain.ewa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EwaTransactionRepository extends JpaRepository<EwaTransaction, Long> {
}
