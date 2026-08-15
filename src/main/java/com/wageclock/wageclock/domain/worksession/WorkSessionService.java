package com.wageclock.wageclock.domain.worksession;

import com.wageclock.wageclock.domain.employment.Employment;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.domain.payperiod.PayPeriod;
import com.wageclock.wageclock.domain.payperiod.PayPeriodRepository;
import com.wageclock.wageclock.global.exception.DuplicateException;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class WorkSessionService {
    private final WorkSessionRepository workSessionRepository;
    private final EmploymentRepository employmentRepository;
    private final PayPeriodRepository payPeriodRepository;

    public WorkSessionService(WorkSessionRepository workSessionRepository,
                              EmploymentRepository employmentRepository,
                              PayPeriodRepository payPeriodRepository) {
        this.workSessionRepository = workSessionRepository;
        this.employmentRepository = employmentRepository;
        this.payPeriodRepository = payPeriodRepository;
    }

    @Transactional
    public ClockInResponse clockIn(ClockInRequest clockInRequest, Long workerId){
        // 정산 마감(closePayPeriod)과 직렬화하기 위한 락.
        // 아래 중복 검사와 세션 생성이 한 트랜잭션 안에서 원자적으로 처리된다.
        Employment employment = employmentRepository.findByIdWithLock(clockInRequest.employmentId())
                .orElseThrow(() -> new NotFoundException("employment not found"));
        if (!employment.getWorkerId().equals(workerId)) {
            throw new UnauthorizedException("unauthorized");
        }
        if(workSessionRepository.existsByEmploymentIdAndStatusNot(clockInRequest.employmentId(),
                WorkSession.WorkSessionStatus.COMPLETED)){
            throw new DuplicateException("this WorkSession already exists");
        }
        PayPeriod payPeriod = payPeriodRepository
                .findByEmployment_IdAndStatusIn(employment.getId(),
                        List.of(PayPeriod.PayPeriodStatus.ACTIVE, PayPeriod.PayPeriodStatus.SETTLING))
                .orElseGet(() -> payPeriodRepository.save(new PayPeriod(employment)));
        // 정산 중에는 새 PayPeriod를 만들지 않고 출근을 막는다.
        // employment당 진행 중인 PayPeriod가 둘이 되면 Optional 조회들이 깨지고,
        // 정산이 무산돼 SETTLING을 ACTIVE로 되돌릴 때 ACTIVE가 두 개가 된다.
        if (payPeriod.getStatus() == PayPeriod.PayPeriodStatus.SETTLING) {
            throw new IllegalStateException("정산이 진행 중이라 출근할 수 없습니다");
        }
        WorkSession workSession = workSessionRepository.save(
                WorkSession.builder()
                        .clockIn(LocalDateTime.now())
                        .employment(employment)
                        .payPeriod(payPeriod)
                        .build());
        return new ClockInResponse(workSession.getId(), workSession.getClockIn(), workSession.getHourlyWage());
    }

    @Transactional
    public ClockOutResponse clockOut(ClockOutRequest clockOutRequest, Long workerId){
        WorkSession workSession = workSessionRepository.findById(clockOutRequest.sessionId())
                .orElseThrow(() -> new NotFoundException("session not found"));
        if(!workSession.getWorkerId().equals(workerId)){
            throw new UnauthorizedException("unauthorized");
        }
        workSession.clockOut();
        workSession.getPayPeriod().addEarnedAmount(workSession.getEarnedAmount());
        return new ClockOutResponse(workSession.getClockOut(), workSession.getEarnedAmount());
    }
    @Transactional
    public PauseResponse pause(Long sessionId, Long workerId){
        WorkSession workSession = workSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("work session not found"));
        if(!workSession.getWorkerId().equals(workerId)){
            throw new UnauthorizedException("unauthorized");
        }
        workSession.pause();
        return new PauseResponse(workSession.getEarnedAmount());
    }
    @Transactional(readOnly = true)
    public Optional<CurrentSessionResponse> getCurrentSession(Long employmentId, Long workerId) {
        Employment employment = employmentRepository.findById(employmentId)
                .orElseThrow(() -> new NotFoundException("employment not found"));
        if (!employment.getWorkerId().equals(workerId)) {
            throw new UnauthorizedException("unauthorized");
        }
        return workSessionRepository
                .findByEmploymentIdAndStatusNot(employmentId, WorkSession.WorkSessionStatus.COMPLETED)
                .map(s -> new CurrentSessionResponse(s.getId(), s.getStatus(),
                        s.getHourlyWage(), s.getEarnedAmount(), s.getLastResumeAt()));
    }

    @Transactional
    public ResumeResponse resume(Long sessionId, Long workerId){
        WorkSession workSession =  workSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("work session not found"));
        if(!workSession.getWorkerId().equals(workerId)){
            throw new UnauthorizedException("unauthorized");
        }
        workSession.resume();
        return new ResumeResponse(workSession.getLastResumeAt());
    }
}