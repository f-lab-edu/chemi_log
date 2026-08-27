package edu.flab.chemilog.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
// Testcontainers 2.x 에서 모듈별 패키지로 옮겨졌다.
// 옛 org.testcontainers.containers.MySQLContainer 도 남아 있지만 deprecated 다.
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * 통합 테스트가 붙을 MySQL 8.4 컨테이너.
 *
 * 인메모리 DB 를 쓰지 않는 이유는 이 스키마가 MySQL 전용이기 때문이다. ENUM, CHECK 제약,
 * 복합 FK, utf8mb4_0900_bin collation 은 H2 가 재현하지 못한다. ddl-auto 도 none 이라
 * 스키마를 만들어 주는 것이 JPA 가 아니라 database/init 의 SQL 파일이다.
 * 테스트가 그 파일을 그대로 먹어야 엔티티와 스키마가 어긋난 것이 여기서 드러난다.
 *
 * 서버 charset 과 collation 을 docker-compose.yml 과 같은 값으로 맞춘다. 서버 기본
 * collation 이 ai_ci 라는 전제 위에서 share_code 와 nickname_key 의 collation 을 따로 지정했으므로,
 * 테스트 서버만 다른 값이면 그 지정이 무엇을 막는지 검증되지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MySqlContainerConfig {

    private static final DockerImageName IMAGE = DockerImageName.parse("mysql:8.4");
    private static final String INIT_DIRECTORY_IN_CONTAINER = "/docker-entrypoint-initdb.d/";

    // Testcontainers 2.x 의 MySQLContainer 는 제네릭이 아니다.
    // self-type 파라미터(MySQLContainer<SELF>)가 없어져서 <?> 를 붙이면 컴파일되지 않는다.
    @Bean
    @ServiceConnection
    MySQLContainer mySqlContainer() {
        MySQLContainer container = new MySQLContainer(IMAGE)
                .withDatabaseName("chemi_log")
                .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

        // mysql 이미지는 최초 기동 때 이 디렉터리의 파일을 이름순으로 실행한다.
        // docker-compose.yml 이 같은 디렉터리를 마운트하는 것과 같은 방식이다.
        for (Path script : initScripts()) {
            container.withCopyFileToContainer(
                    MountableFile.forHostPath(script),
                    INIT_DIRECTORY_IN_CONTAINER + script.getFileName());
        }
        return container;
    }

    /**
     * 파일 이름을 코드에 적지 않고 디렉터리를 읽는다. 스키마 파일이 늘어났을 때
     * 테스트만 조용히 옛 스키마로 도는 것을 막으려는 것이다.
     */
    private List<Path> initScripts() {
        Path initDirectory = Path.of("..", "database", "init").toAbsolutePath().normalize();
        if (!Files.isDirectory(initDirectory)) {
            throw new IllegalStateException(
                    "스키마 SQL 디렉터리를 찾지 못했다. 테스트 실행 디렉터리가 backend/ 가 아닐 수 있다. path="
                            + initDirectory);
        }
        try (Stream<Path> files = Files.list(initDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
