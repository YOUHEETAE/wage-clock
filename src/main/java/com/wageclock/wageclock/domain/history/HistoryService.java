package com.wageclock.wageclock.domain.history;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HistoryService {

    private final HistoryRepository historyRepository;
    private final EmploymentRepository employmentRepository;

    public HistoryService(HistoryRepository historyRepository, EmploymentRepository employmentRepository) {
        this.historyRepository = historyRepository;
        this.employmentRepository = employmentRepository;
    }

    @Transactional(readOnly = true)
    public HistoryResponse getHistories(Long employmentId, Long callerId){
        Employment employment = employmentRepository.findById(employmentId).orElseThrow(
                () -> new NotFoundException("Employment not found")
        );
        if(!employment.getEmployerId().equals(callerId) && !employment.getWorkerId().equals(callerId)){
            throw new UnauthorizedException("Unauthorized");
        }
        List<HistoryEvent> historyEvents = historyRepository.getHistory(employmentId);
        return new HistoryResponse(employmentId, historyEvents);
    }
}
