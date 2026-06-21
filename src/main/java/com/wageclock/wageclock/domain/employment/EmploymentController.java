package com.wageclock.wageclock.domain.employment;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/employments")
public class EmploymentController {
    private final EmploymentService employmentService;

    public EmploymentController(EmploymentService employmentService) {
        this.employmentService = employmentService;
    }

    @Operation(summary = "고용 관계 등록 (고용주가 근로자 채용)")
    @PostMapping
    public EmploymentResponse createEmployment(@RequestBody EmploymentRequest employmentRequest,
                                               @AuthenticationPrincipal Long employerId) {
        return employmentService.createEmployment(employmentRequest, employerId);
    }
}
