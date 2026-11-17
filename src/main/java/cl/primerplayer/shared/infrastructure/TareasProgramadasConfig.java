package cl.primerplayer.shared.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * Tareas programadas ({@code @Scheduled}) con ShedLock: aunque haya varias instancias de la API, cada tarea corre en
 * una sola a la vez (tabla {@code shedlock}, V5). Se desactiva con {@code pp.tareas.habilitadas=false} (pruebas).
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
@ConditionalOnProperty(name = "pp.tareas.habilitadas", havingValue = "true", matchIfMissing = true)
class TareasProgramadasConfig {

    @Bean
    LockProvider lockProvider(JdbcTemplate jdbc) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbc)
                .usingDbTime()   // la hora de PostgreSQL, no la de cada instancia
                .build());
    }
}
