package com.wageclock.wageclock.domain.auth;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.global.exception.DuplicateException;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import com.wageclock.wageclock.global.security.JwtProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private final EmployerRepository employerRepository;
    private final WorkerRepository workerRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JwtProvider jwtProvider;
    private final RedisTemplate<String, String> redisTemplate;

    public AuthService(EmployerRepository employerRepository, WorkerRepository workerRepository,
                       BCryptPasswordEncoder bCryptPasswordEncoder, JwtProvider jwtProvider,
                       RedisTemplate<String, String> redisTemplate) {
        this.employerRepository = employerRepository;
        this.workerRepository = workerRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.jwtProvider = jwtProvider;
        this.redisTemplate = redisTemplate;
    }

    public LoginResponse login(LoginRequest loginRequest) {
        String name;
        String encodedPassword;
        Long id;
        UserRole role;

        Optional<Employer> employerOpt = employerRepository.findByEmail(loginRequest.email());
        if (employerOpt.isPresent()) {
            Employer employer = employerOpt.get();
            encodedPassword = employer.getPassword();
            id = employer.getId();
            name = employer.getName();
            role = UserRole.EMPLOYER;
        } else {
            Worker worker = workerRepository.findByEmail(loginRequest.email())
                    .orElseThrow(() -> new NotFoundException("user not found"));
            encodedPassword = worker.getPassword();
            id = worker.getId();
            name = worker.getName();
            role = UserRole.WORKER;
        }

        if (!bCryptPasswordEncoder.matches(loginRequest.password(), encodedPassword)) {
            throw new UnauthorizedException("invalid password");
        }
        String token = jwtProvider.generateToken(id, role);
        return new LoginResponse(name, loginRequest.email(), role, token);
    }

    public void logout(String token) {
        long ttl = jwtProvider.getClaims(token).getExpiration().getTime() - System.currentTimeMillis();
        redisTemplate.opsForValue().set("blacklist:" + token, "true", ttl, TimeUnit.MILLISECONDS);
    }

    public void signup(SignupRequest signupRequest) {
        if(employerRepository.existsByEmail(signupRequest.email()) || workerRepository.existsByEmail(signupRequest.email())) {
            throw new DuplicateException("email already exists");
        }
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