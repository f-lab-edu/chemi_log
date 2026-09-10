package edu.flab.chemilog;

import edu.flab.chemilog.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * 컨텍스트 로딩 확인.
 *
 * 맨 @SpringBootTest 로 두면 DB 없이 띄우게 되어 Hibernate 가 "Unable to determine Dialect" 로
 * 죽습니다. datasource 설정에 기본값 없는 ${DB_PASSWORD} 가 있기 때문입니다.
 * 원인이 잘 드러나지 않는 오류라 DB 문제를 컨텍스트 설정 문제로 오해하게 됩니다.
 *
 * @IntegrationTest 를 그대로 쓰는 것은 컨텍스트 캐시 때문이기도 합니다. 애노테이션 조합이
 * 다르면 Spring 이 컨텍스트를 새로 띄우고 MySQL 컨테이너도 다시 시작합니다.
 */
@IntegrationTest
class ChemiLogApplicationTests {

    @Test
    void contextLoads() {
    }
}
