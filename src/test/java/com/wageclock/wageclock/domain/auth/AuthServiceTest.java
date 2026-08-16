package com.wageclock.wageclock.domain.auth;

import com.wageclock.wageclock.domain.employer.Employer;
import com.wageclock.wageclock.domain.employer.EmployerRepository;
import com.wageclock.wageclock.domain.worker.WorkerRepository;
import com.wageclock.wageclock.global.exception.DuplicateException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    // 계정 존재 여부를 드러내지 않도록 비밀번호 불일치와 같은 예외로 답한다
    @Test
    void 존재하지_않는_이메일_로그인(){
        when(employerRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(workerRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        LoginRequest loginRequest = new LoginRequest("other@test.com", "password");
        assertThrows(UnauthorizedException.class, () -> authService.login(loginRequest));
    }

    // 응답 시간까지 같아야 하므로, 계정이 없어도 대조용 해시로 검증을 한 번 수행한다
    @Test
    void 존재하지_않는_이메일도_해시_검증을_수행한다(){
        when(employerRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(workerRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        LoginRequest loginRequest = new LoginRequest("other@test.com", "password");

        assertThrows(UnauthorizedException.class, () -> authService.login(loginRequest));

        verify(bCryptPasswordEncoder).matches(eq("password"), any());
    }

    @Test
    void 존재하지_않는_비밀번호_로그인(){
        when(employerRepository.findByEmail(anyString())).thenReturn(Optional.of(employer));
        LoginRequest loginRequest = new LoginRequest("test@test.com", "wrongPassword");
        assertThrows(UnauthorizedException.class, () -> authService.login(loginRequest));
    }

    @Test
    void 로그인_성공_시_응답_반환(){
        when(employerRepository.findByEmail("test@test.com")).thenReturn(Optional.of(employer));
        when(jwtProvider.generateToken(any(), any())).thenReturn("token");
        when(bCryptPasswordEncoder.matches("password","password")).thenReturn(true);
        LoginRequest loginRequest = new LoginRequest("test@test.com", "password");
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
    void 중복_이메일_회원가입_시_예외_employer_테이블_충돌(){
        when(employerRepository.existsByEmail(anyString())).thenReturn(true);
        SignupRequest signupRequest = new SignupRequest("홍길동", "test@test.com", "password", UserRole.WORKER);
        assertThrows(DuplicateException.class, () -> authService.signup(signupRequest));
    }

    @Test
    void 중복_이메일_회원가입_시_예외_worker_테이블_충돌(){
        when(employerRepository.existsByEmail(anyString())).thenReturn(false);
        when(workerRepository.existsByEmail(anyString())).thenReturn(true);
        SignupRequest signupRequest = new SignupRequest("홍길동", "test@test.com", "password", UserRole.EMPLOYER);
        assertThrows(DuplicateException.class, () -> authService.signup(signupRequest));
    }
    @Test
    void 정상_로그아웃_검증(){
        when(jwtProvider.validateToken("token")).thenReturn(true);
        when(jwtProvider.getClaims("token")).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() + 3600000));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        authService.logout("token");
        verify(valueOperations).set(contains("blacklist:"), eq("true"), anyLong(), any());
    }

    // 만료·위조 토큰은 파싱 자체가 예외라, 검증 없이 진입하면 로그아웃이 500으로 끝난다
    @Test
    void 유효하지_않은_토큰_로그아웃은_무시(){
        when(jwtProvider.validateToken("expired")).thenReturn(false);

        authService.logout("expired");

        verify(jwtProvider, never()).getClaims(anyString());
        verifyNoInteractions(redisTemplate);
    }

    // 검증 통과 후 저장 직전에 만료되면 TTL이 음수가 되고 Redis가 거부한다
    @Test
    void 만료_직전_토큰은_블랙리스트에_넣지_않는다(){
        when(jwtProvider.validateToken("token")).thenReturn(true);
        when(jwtProvider.getClaims("token")).thenReturn(claims);
        when(claims.getExpiration()).thenReturn(new Date(System.currentTimeMillis() - 1));

        authService.logout("token");

        verifyNoInteractions(redisTemplate);
    }
}
