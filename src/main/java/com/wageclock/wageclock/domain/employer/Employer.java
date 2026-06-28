package com.wageclock.wageclock.domain.employer;

import com.wageclock.wageclock.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "employers")
@Getter
@NoArgsConstructor
public class Employer extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false,  unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Builder
    public Employer(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }


}
