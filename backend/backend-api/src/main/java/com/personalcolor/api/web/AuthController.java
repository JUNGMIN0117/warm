package com.personalcolor.api.web;

import com.personalcolor.api.security.JwtService;
import com.personalcolor.api.web.dto.AuthDtos;
import com.personalcolor.domain.user.AuthenticateUser;
import com.personalcolor.domain.user.RegisterUser;
import com.personalcolor.domain.user.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원가입과 로그인. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterUser registerUser;
    private final AuthenticateUser authenticateUser;
    private final JwtService jwtService;

    public AuthController(RegisterUser registerUser, AuthenticateUser authenticateUser,
                          JwtService jwtService) {
        this.registerUser = registerUser;
        this.authenticateUser = authenticateUser;
        this.jwtService = jwtService;
    }

    /**
     * 회원가입.
     *
     * <p>가입 직후 토큰을 함께 발급한다. 가입하고 다시 로그인하게 만드는 것은
     * 불필요한 마찰이고, 방금 비밀번호를 확인한 참이라 보안상 손해도 없다.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(
            @Valid @RequestBody AuthDtos.RegisterRequest request) {
        User user = registerUser.execute(
                request.email(), request.displayName(), request.password());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(user));
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return toResponse(authenticateUser.execute(request.email(), request.password()));
    }

    private AuthDtos.AuthResponse toResponse(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new AuthDtos.AuthResponse(
                token.value(), token.expiresAt(),
                user.id().toString(), user.displayName());
    }
}
