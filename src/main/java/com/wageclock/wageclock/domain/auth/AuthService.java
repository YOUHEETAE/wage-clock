package com.wageclock.wageclock.domain.auth;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.worker.Worker;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.global.exception.DuplicateException;
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
    private final String dummyPasswordHash;

    public AuthService(EmployerRepository employerRepository, WorkerRepository workerRepository,
                       BCryptPasswordEncoder bCryptPasswordEncoder, JwtProvider jwtProvider,
                       RedisTemplate<String, String> redisTemplate) {
        this.employerRepository = employerRepository;
        this.workerRepository = workerRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.jwtProvider = jwtProvider;
        this.redisTemplate = redisTemplate;
        // 계정이 없을 때 비교할 대조용 해시. 상수로 박지 않고 실제 인코더로 만들어
        // cost factor가 항상 운영 설정과 같게 유지한다.
        this.dummyPasswordHash = bCryptPasswordEncoder.encode("account-absent-placeholder");
    }

    /** 로그인 대상이 고용주인지 근로자인지 가린 뒤의 공통 정보. */
    private record Account(Long id, String name, String encodedPassword, UserRole role) {}

    /**
     * 응답도 응답 시간도 계정 존재 여부를 드러내지 않아야 한다.
     * <p>
     * 없는 이메일과 틀린 비밀번호를 같은 401로 답하는 것만으로는 부족하다. 계정을 못 찾았을 때
     * 즉시 반환하면 bcrypt 검증(수십~수백 ms)을 건너뛰어 응답이 눈에 띄게 빨라지고,
     * 그 시간 차이로 가입 여부를 알아낼 수 있다. 그래서 계정이 없어도 대조용 해시로
     * 검증을 한 번 수행한 뒤 실패시킨다.
     */
    public LoginResponse login(LoginRequest loginRequest) {
        Account account = findAccount(loginRequest.email());
        // 단락 평가로 bcrypt를 건너뛰지 않도록 먼저 계산한다
        boolean passwordMatches = bCryptPasswordEncoder.matches(loginRequest.password(),
                account == null ? dummyPasswordHash : account.encodedPassword());
        if (account == null || !passwordMatches) {
            throw new UnauthorizedException("invalid email or password");
        }
        String token = jwtProvider.generateToken(account.id(), account.role());
        return new LoginResponse(account.name(), loginRequest.email(), account.role(), token);
    }

    private Account findAccount(String email) {
        Optional<Employer> employerOpt = employerRepository.findByEmail(email);
        if (employerOpt.isPresent()) {
            Employer employer = employerOpt.get();
            return new Account(employer.getId(), employer.getName(),
                    employer.getPassword(), UserRole.EMPLOYER);
        }
        return workerRepository.findByEmail(email)
                .map(worker -> new Account(worker.getId(), worker.getName(),
                        worker.getPassword(), UserRole.WORKER))
                .orElse(null);
    }

    public void logout(String token) {
        // 만료·위조된 토큰은 JWT 검증에서 이미 걸러지므로 블랙리스트에 넣을 필요가 없다.
        // 먼저 파싱하면 ExpiredJwtException이 그대로 올라가 로그아웃이 500으로 끝난다.
        if (!jwtProvider.validateToken(token)) {
            return;
        }
        long ttl = jwtProvider.getClaims(token).getExpiration().getTime() - System.currentTimeMillis();
        // 검증과 이 계산 사이에 만료됐을 수 있다. 음수 TTL은 Redis가 거부한다.
        if (ttl <= 0) {
            return;
        }
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