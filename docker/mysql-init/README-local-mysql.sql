-- 로컬에 이미 설치된 MySQL 을 이 프로젝트에 맞추는 스크립트.
--
-- 이 디렉토리는 docker-compose.yml 이 컨테이너의 docker-entrypoint-initdb.d 로 마운트하므로,
-- Docker 를 쓰는 경우 이 스크립트는 **컨테이너 최초 기동 시 자동 실행됩니다.**
-- 다만 그 자동 실행은 MySQL 데이터 디렉토리가 비어 있을 때만 일어납니다.
-- 이 파일이 생기기 전에 이미 `docker compose up -d` 를 돌려본 적이 있다면 볼륨이 이미
-- 초기화되어 있어서 실행되지 않고, `./gradlew test` 가 "Unknown database duckspace_test" 로
-- 실패합니다. 그때는 아래 둘 중 하나를 하세요.
--
--   (A) 볼륨 유지 — 컨테이너 안에서 이 스크립트만 직접 실행 (권장)
--       docker compose exec -T mysql mysql -uroot -pduckspace < docker/mysql-init/README-local-mysql.sql
--
--   (B) 볼륨 초기화 — 로컬 데이터가 전부 지워집니다
--       docker compose down -v && docker compose up -d
--
-- Docker 없이 직접 설치한 MySQL 이라면 자기 터미널에서 mysql 에 접속한 뒤
--   source docker/mysql-init/README-local-mysql.sql
-- 로 실행하세요. (PowerShell 은 `<` 리다이렉트를 지원하지 않습니다)

CREATE DATABASE IF NOT EXISTS duckspace
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS duckspace_test
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'duckspace'@'localhost' IDENTIFIED BY 'duckspace';
CREATE USER IF NOT EXISTS 'duckspace'@'%'         IDENTIFIED BY 'duckspace';

GRANT ALL PRIVILEGES ON duckspace.*      TO 'duckspace'@'localhost';
GRANT ALL PRIVILEGES ON duckspace_test.* TO 'duckspace'@'localhost';
GRANT ALL PRIVILEGES ON duckspace.*      TO 'duckspace'@'%';
GRANT ALL PRIVILEGES ON duckspace_test.* TO 'duckspace'@'%';

FLUSH PRIVILEGES;
