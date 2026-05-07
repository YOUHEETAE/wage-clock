package com.wageclock.wageclock.domain.worksession;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.global.exception.DuplicateException;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class WorkSessionService {
    private final WorkSessionRepository workSessionRepository;
    private final EmploymentRepository employmentRepository;

    public WorkSessionService(WorkSessionRepository workSessionRepository,
                              EmploymentRepository employmentRepository) {
        this.workSessionRepository = workSessionRepository;
        this.employmentRepository = employmentRepository;
    }

    @Transactional
    public ClockInResponse clockIn(ClockInRequest clockInRequest, Long workerId){
        Employment employment = employmentRepository.findById(clockInRequest.employmentId())
                .orElseThrow(() -> new NotFoundException("employment not found"));
        if (!employment.getWorkerId().equals(workerId)) {
            throw new UnauthorizedException("unauthorized");
        }
        if(workSessionRepository.existsByEmploymentIdAndStatus(clockInRequest.employmentId(),
                WorkSession.WorkSessionStatus.WORKING)){
            throw new DuplicateException("this WorkSession already exists");
        }
        WorkSession workSession = workSessionRepository.save(WorkSession.builder()
                .clockIn(LocalDateTime.now()).employment(employment).build());
        return new ClockInResponse(workSession.getId(), workSession.getClockIn());
    }

    @Transactional
    public ClockOutResponse clockOut(ClockOutRequest clockOutRequest, Long workerId){
        WorkSession workSession = workSessionRepository.findById(clockOutRequest.sessionId())
                .orElseThrow(() -> new NotFoundException("session not found"));
        if(!workSession.getWorkerId().equals(workerId)){
            throw new UnauthorizedException("unauthorized");
        }
        workSession.clockOut();
        workSessionRepository.save(workSession);
        return new ClockOutResponse(workSession.getClockOut(), workSession.getEarnedAmount());
    }
}