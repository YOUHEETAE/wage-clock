package com.wageclock.wageclock.domain.auth;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.global.exception.DuplicateException;
import com.wageclock.wageclock.global.exception.NotFoundException;
import com.wageclock.wageclock.global.exception.UnauthorizedException;
import com.wageclock.wageclock.global.security.JwtProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private BCryptPasswordEncoder bCryptPasswordEncoder;
    @Mock
    private EmployerRepository employerRepository;
    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private RedisTemplate<String, String> redisTemplate;
    @Mock
    Claims claims;
    @Mock
    ValueOperations valueOperations;

    Employer employer;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        employer = Employer.builder()
                .name("홍길동")
                .email("test@test.com")
                .password("password")
                .build();
    }

    @Test
    void 존재하지_않는_이메일_로그인(){
        when(employerRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        LoginRequest loginRequest = new LoginRequest("other@test.com", "password", UserRole.EMPLOYER);
        assertThrows(NotFoundException.class, () -> authService.login(loginRequest));
    }
    @Test
    void 존재하지_않는_비밀번호_로그인(){
        when(employerRepository.findByEmail(anyString())).thenReturn(Optional.of(employer));
        LoginRequest loginRequest = new LoginRequest("test@test.com", "wrongPassword", UserRole.EMPLOYER);
        assertThrows(UnauthorizedException.class, () -> authService.login(loginRequest));
    }

    @Test
    void 로그인_성공_시_응답_반환(){
        when(employerRepository.findByEmail("test@test.com")).thenReturn(Optional.of(employer));
        when(jwtProvider.generateToken(any(), any())).thenReturn("token");
        when(bCryptPasswordEncoder.matches("password","password")).thenReturn(true);
        LoginRequest loginRequest = new LoginRequest("test@test.com", "password", UserRole.EMPLOYER);
        LoginResponse loginResponse = new LoginResponse("홍길동", "test@test.com", UserRole.EMPLOYER, "token");
        assertEquals(loginResponse, authService.login(loginRequest));
    }

    @Test
    void employer_회원가입_검증(){
        SignupRequest signupEmployerRequest = new SignupRequest("아무개", "other@test.com",
                "password", UserRole.EMPLOYER);
        authService.signup(signupEmployerRequest);
        verify(employerRepository).save(any());
    }

    @Test
    void worker_회원가입_검증(){
        SignupRequest signupWorkerRequest = new SignupRequest("아무개", "other@test.com",
                "password", UserRole.WORKER);
        authService.signup(signupWorkerRequest);
        verify(workerRepository).save(any());
    }
    @Test
    void 중복_이메일_회원가입_시_예외(){
        when(employerRepository.existsByEmail(anyString())).thenReturn(true);
        SignupRequest signupEmployerRequest = new SignupRequest("홍길동", "test@test.com",
                "password", UserRole.EMPLOYER);
        assertThrows(DuplicateException.class, () -> authService.signup(signupEmployerRequest));
    }
    @Test
    void 정상_로그아웃_검증(){
        when(jwtProvider.getClaims("token")).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 3600000));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        authService.logout("token");
        verify(valueOperations).set(contains("blacklist:"), eq("true"), anyLong(), any());
    }
}
