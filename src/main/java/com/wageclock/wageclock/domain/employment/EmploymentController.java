package com.wageclock.wageclock.domain.employment;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employment")
public class EmploymentController {
    private final EmploymentService employmentService;

    public EmploymentController(EmploymentService employmentService) {
        this.employmentService = employmentService;
    }

    @PostMapping
    public CreateEmploymentResponse createEmployment(@RequestBody CreateEmploymentRequest createEmploymentRequest,
                                                     @AuthenticationPrincipal Long employerId) {
        return employmentService.createEmployment(createEmploymentRequest, employerId);
    }
}
