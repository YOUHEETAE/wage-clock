package com.wageclock.wageclock.domain.worksession;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    public ClockInResponse clockIn (ClockInRequest clockInRequest){
       Long workerId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

       Employment employment = employmentRepository.findById(clockInRequest.employmentId())
               .orElseThrow(() -> new RuntimeException("employment not found"));
        if (!employment.getWorker().getId().equals(workerId)) {
            throw new RuntimeException("unauthorized");
        }
        if(workSessionRepository.existsByEmploymentIdAndStatus(clockInRequest.employmentId(),
                WorkSession.WorkSessionStatus.WORKING)){
            throw new RuntimeException("this WorkSession already exists");
        }
        WorkSession workSession =  workSessionRepository.save(WorkSession.builder()
               .clockIn(LocalDateTime.now()).employment(employment).build());
       return new ClockInResponse(workSession.getId(), workSession.getClockIn());
    }

    public ClockOutResponse clockOut (ClockOutRequest clockOutRequest){
        Long workerId = (Long)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        WorkSession workSession = workSessionRepository.findById(clockOutRequest.sessionId())
                .orElseThrow(() -> new RuntimeException("session not found"));
        if(!workSession.getEmployment().getWorker().getId().equals(workerId)){
            throw new RuntimeException("unauthorized");
        }
        workSession.clockOut();
        workSessionRepository.save(workSession);
        return new ClockOutResponse(workSession.getClockOut(), workSession.getEarnedAmount());
    }
}
