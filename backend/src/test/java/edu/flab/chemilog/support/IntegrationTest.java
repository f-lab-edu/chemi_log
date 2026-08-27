package edu.flab.chemilog.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
// Boot 4 에서 spring-boot-webmvc-test 모듈로 옮겨졌다.
// Boot 3 의 org.springframework.boot.test.autoconfigure.web.servlet 은 이제 없다.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * 실제 MySQL 컨테이너에 붙는 통합 테스트.
 *
 * DB_PASSWORD 를 여기서 주는 이유는 application.yml 의 datasource 설정이
 * 기본값 없는 ${DB_PASSWORD} 를 참조하기 때문이다. 실제 접속 정보는 @ServiceConnection 이
 * 컨테이너 값으로 덮으므로 여기 값 자체는 쓰이지 않는다. 운영 설정에 기본 비밀번호를
 * 넣지 않으려고 테스트 쪽에서 채운다.
 *
 * 설정을 한 곳에 모은 이유는 테스트 컨텍스트 캐시 때문이다. 클래스마다 애노테이션 조합이
 * 조금씩 달라지면 Spring 이 컨텍스트를 새로 띄우고 컨테이너도 다시 시작한다.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest(properties = "DB_PASSWORD=testcontainers")
@AutoConfigureMockMvc
@Import(MySqlContainerConfig.class)
public @interface IntegrationTest {
}
