package com.wageclock.wageclock.domain.workplace;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "workplaces")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Workplace extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id", nullable = false)
    private Employer employer;

    @Column(nullable = false)
    private String name;

    @Column
    private String address;

    @Builder
    public Workplace(Employer employer, String name, String address) {
        this.employer = employer;
        this.name = name;
        this.address = address;
    }

    public Long getEmployerId() {
        return employer.getId();
    }
}
