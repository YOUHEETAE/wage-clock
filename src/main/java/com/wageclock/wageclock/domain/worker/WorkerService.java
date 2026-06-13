package com.wageclock.wageclock.domain.worker;

import com.wageclock.wageclock.global.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class WorkerService {

    private final WorkerRepository workerRepository;

    public WorkerService(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }
    @Transactional
    public void registerAccountInfo(Long workerId, RegisterAccountInfo registerAccountInfo){
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new NotFoundException("Worker not found"));
        worker.registerAccountInfo(registerAccountInfo.accountNumber(),
                registerAccountInfo.bankCode(),
                registerAccountInfo.accountHolder());
        workerRepository.save(worker);
    }
}
