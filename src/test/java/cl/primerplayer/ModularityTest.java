package cl.primerplayer;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** Falla si un módulo usa algo distinto del {@code api}/{@code events} de otro módulo o hay ciclos. */
class ModularityTest {

    @Test
    void lasFronterasDeModulosSeRespetan() {
        ApplicationModules.of(PrimerPlayerApiApplication.class).verify();
    }
}
