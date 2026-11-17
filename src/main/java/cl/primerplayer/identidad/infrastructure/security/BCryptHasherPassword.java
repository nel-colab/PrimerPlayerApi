package cl.primerplayer.identidad.infrastructure.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import cl.primerplayer.identidad.application.port.out.HasherPassword;

/** Adaptador de salida: BCrypt (costo 12). El hash (60 caracteres) cabe en {@code password_hash VARCHAR(100)}. */
@Component
class BCryptHasherPassword implements HasherPassword {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final String senuelo = encoder.encode("senuelo-para-tiempo-constante");

    @Override
    public String hashear(String passwordPlano) {
        return encoder.encode(passwordPlano);
    }

    @Override
    public boolean coincide(String passwordPlano, String hash) {
        return encoder.matches(passwordPlano, hash);
    }

    @Override
    public String hashSenuelo() {
        return senuelo;
    }
}
