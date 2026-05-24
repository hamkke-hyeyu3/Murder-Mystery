package com.murdermystery.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// dev 환경에서 변경된 마이그레이션(V14 등)의 체크섬 불일치를 자동으로 수정한다.
// repair()는 flyway_schema_history의 체크섬을 현재 파일 기준으로 갱신하고 FAILED 레코드를 제거한다.
@Component
@Profile("dev")
class FlywayRepairMigrationStrategy implements FlywayMigrationStrategy {

    @Override
    public void migrate(Flyway flyway) {
        flyway.repair();
        flyway.migrate();
    }
}
