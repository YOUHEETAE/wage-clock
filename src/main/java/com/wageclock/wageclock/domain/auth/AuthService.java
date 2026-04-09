package com.wageclock.wageclock.domain.auth;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.global.security.JwtProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final EmployerRepository employerRepository;
    private final WorkerRepository workerRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(EmployerRepository employerRepository, WorkerRepository workerRepository,
                       BCryptPasswordEncoder bCryptPasswordEncoder, JwtProvider jwtProvider) {
        this.employerRepository = employerRepository;
        this.workerRepository = workerRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.jwtProvider = jwtProvider;
    }

    public LoginResponse login(LoginRequest loginRequest) {
        String name;
        String encodedPassword;
        Long id;
        String token;
        if(loginRequest.role() == UserRole.EMPLOYER){
            Employer employer = employerRepository.findByEmail(loginRequest.email())
                    .orElseThrow(()->new RuntimeException("employer not found"));
            encodedPassword = employer.getPassword();
            id = employer.getId();
            name = employer.getName();
        } else {
            Worker worker = workerRepository.findByEmail(loginRequest.email())
                    .orElseThrow(()->new RuntimeException("worker not found"));
            encodedPassword = worker.getPassword();
            id = worker.getId();
            name = worker.getName();
        }
        if (!bCryptPasswordEncoder.matches(loginRequest.password(), encodedPassword)) {
            throw new RuntimeException("invalid password");
        }
        token = jwtProvider.generateToken(id, loginRequest.role());
        return new LoginResponse(name, loginRequest.email(), loginRequest.role(), token);
    }

    public void signup(SignupRequest signupRequest) {
        if(signupRequest.role() == UserRole.EMPLOYER){
            employerRepository.save(Employer.builder()
                    .name(signupRequest.name())
                    .email(signupRequest.email())
                    .password(bCryptPasswordEncoder.encode(signupRequest.password()))
                    .build());
        } else {
            workerRepository.save(Worker.builder()
                    .name(signupRequest.name())
                    .email(signupRequest.email())
                    .password(bCryptPasswordEncoder.encode(signupRequest.password()))
                    .build());
        }
    }
}
