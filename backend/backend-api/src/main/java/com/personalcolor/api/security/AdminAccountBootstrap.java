package com.personalcolor.api.security;

import com.personalcolor.domain.user.RegisterUser;
import com.personalcolor.domain.user.Role;
import com.personalcolor.domain.user.User;
import com.personalcolor.domain.user.port.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 관리자 계정 부트스트랩 — 가입 API가 아니라 기동 경로로만 (ADR-011).
 *
 * <p>왜 시드(Flyway)가 아닌가: 시드에 넣으려면 비밀번호(해시라도)가
 * 저장소에 남는다. JWT 서명 키에 기본값을 두지 않는 것과 같은 이유로,
 * 관리자 자격증명도 환경변수로만 받는다. 키와 다른 점 하나 — 이 값은
 * <b>없어도 기동한다.</b> 관리자 기능은 서비스의 본체(분석)가 아니라
 * 부가 기능이라, 없다고 전체를 세우는 것은 과잉이다.
 *
 * <p>동작: 이메일이 설정되어 있으면 해당 계정을 만들거나(비밀번호 규칙은
 * 일반 가입과 동일 — 10자 이상) 이미 있으면 ADMIN으로 승격한다.
 * <b>기존 계정의 비밀번호는 절대 덮어쓰지 않는다</b> — 환경변수의 옛
 * 비밀번호가 매 기동마다 현재 비밀번호를 되돌리는 사고를 막는다.
 */
@Component
public class AdminAccountBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountBootstrap.class);

    private final UserRepository users;
    private final RegisterUser registerUser;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountBootstrap(
            UserRepository users,
            RegisterUser registerUser,
            @Value("${pcai.admin.email:}") String adminEmail,
            @Value("${pcai.admin.password:}") String adminPassword) {
        this.users = users;
        this.registerUser = registerUser;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank()) {
            log.info("관리자 부트스트랩 비활성 — PCAI_ADMIN_EMAIL이 설정되지 않았습니다.");
            return;
        }

        users.findByEmail(adminEmail.strip().toLowerCase()).ifPresentOrElse(
                this::promoteIfNeeded,
                this::createAdmin);
    }

    private void promoteIfNeeded(User existing) {
        if (existing.role() == Role.ADMIN) {
            log.info("관리자 계정 확인: {} (이미 ADMIN)", existing.email());
            return;
        }
        users.save(existing.withRole(Role.ADMIN));
        log.info("기존 계정을 관리자로 승격: {}", existing.email());
    }

    private void createAdmin() {
        if (adminPassword.isBlank()) {
            log.warn("관리자 부트스트랩 실패 — 계정이 없고 PCAI_ADMIN_PASSWORD도 없습니다.");
            return;
        }
        // RegisterUser를 그대로 태워 비밀번호 규칙(10자+)도 동일하게 적용한다.
        User created = registerUser.execute(adminEmail, "관리자", adminPassword);
        users.save(created.withRole(Role.ADMIN));
        log.info("관리자 계정 생성: {}", created.email());
    }
}
