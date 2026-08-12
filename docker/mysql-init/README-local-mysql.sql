-- 로컬에 이미 설치된 MySQL 을 이 프로젝트에 맞추는 스크립트.
-- (Docker 를 쓰는 경우에는 docker-compose 가 자동으로 처리하므로 실행할 필요가 없습니다.)
--
-- 실행:  mysql -u root -p < docker/mysql-init/README-local-mysql.sql

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
