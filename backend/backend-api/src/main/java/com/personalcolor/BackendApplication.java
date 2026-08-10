package com.personalcolor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 실행 진입점.
 *
 * <p>이 클래스를 {@code com.personalcolor} 루트 패키지에 두는 것이
 * 의도적인 선택이다. Spring Boot의 컴포넌트 스캔·엔티티 스캔·리포지토리
 * 스캔은 모두 이 클래스의 패키지를 기준점으로 삼는데, 루트에 두면
 * 세 모듈({@code domain}, {@code infrastructure}, {@code api})이
 * 전부 그 아래에 들어와 자동으로 덮인다.
 *
 * <p>대안은 애플리케이션 클래스를 하위 패키지에 두고
 * {@code @ComponentScan}·{@code @EntityScan}·{@code @EnableJpaRepositories}로
 * 범위를 넓히는 것이었다. 애너테이션 셋이 늘고, 모듈을 추가할 때마다
 * 갱신해야 하며, 빠뜨리면 "빈을 찾을 수 없음"으로 런타임에 드러난다.
 * 패키지 배치 하나로 없앨 수 있는 문제를 설정으로 푸는 셈이라 택하지 않았다.
 */
@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
