-- 테스트 전용 DB. 개발용 duckspace 데이터를 테스트가 지우지 않도록 분리합니다.
CREATE DATABASE IF NOT EXISTS duckspace_test
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON duckspace_test.* TO 'duckspace'@'%';
FLUSH PRIVILEGES;
