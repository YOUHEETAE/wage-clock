package com.wageclock.wageclock.domain.history;

import com.wageclock.wageclock.domain.auth.UserRole;
import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
    public HistoryResponse getHistories(Long employmentId, Long callerId, UserRole role, String after, int size){
        Employment employment = employmentRepository.findById(employmentId).orElseThrow(
                () -> new NotFoundException("Employment not found")
        );
        boolean authorized = switch (role) {
            case EMPLOYER -> employment.getEmployerId().equals(callerId);
            case WORKER -> employment.getWorkerId().equals(callerId);
        };
        if (!authorized) throw new UnauthorizedException("Unauthorized");
        Timestamp cursor = after != null ? Timestamp.valueOf(LocalDateTime.parse(after)) : null;
        List<HistoryEvent> historyEvents = historyRepository.getHistory(employmentId, cursor, size + 1);
        boolean hasNext = historyEvents.size() > size;
        if(hasNext) historyEvents = historyEvents.subList(0, size);
        String nextCursor = hasNext ? historyEvents.getLast().timestamp().toString() : null;
        return new HistoryResponse(employmentId, nextCursor, hasNext, historyEvents);
    }
}
