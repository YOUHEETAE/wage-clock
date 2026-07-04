package com.wageclock.wageclock.domain.workplace;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.employment.EmploymentRepository;
import com.wageclock.wageclock.global.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WorkplaceService {

    private final WorkplaceRepository workplaceRepository;
    private final EmployerRepository employerRepository;
    private final EmploymentRepository employmentRepository;

    public WorkplaceService(WorkplaceRepository workplaceRepository,
                            EmployerRepository employerRepository,
                            EmploymentRepository employmentRepository) {
        this.workplaceRepository = workplaceRepository;
        this.employerRepository = employerRepository;
        this.employmentRepository = employmentRepository;
    }

    @Transactional
    public WorkplaceResponse createWorkplace(WorkplaceRequest request, Long employerId) {
        Employer employer = employerRepository.findById(employerId)
                .orElseThrow(() -> new NotFoundException("employer not found"));
        Workplace workplace = workplaceRepository.save(
                Workplace.builder()
                        .employer(employer)
                        .name(request.name())
                        .address(request.address())
                        .build());
        return toResponse(workplace);
    }

    @Transactional(readOnly = true)
    public List<WorkplaceResponse> getWorkplaces(Long employerId) {
        return workplaceRepository.findAllByEmployer_Id(employerId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerWorkplaceResponse> getWorkerWorkplaces(Long workerId) {
        return employmentRepository.findByWorker_Id(workerId).stream()
                .map(e -> new WorkerWorkplaceResponse(
                        e.getId(), e.getWorkplaceId(), e.getWorkplaceName(), e.getWorkplaceAddress()))
                .toList();
    }

    private WorkplaceResponse toResponse(Workplace workplace) {
        return new WorkplaceResponse(workplace.getId(), workplace.getName(), workplace.getAddress());
    }
}
