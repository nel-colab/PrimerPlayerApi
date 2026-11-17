package cl.primerplayer.catalogo.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Immutable
@Table(name = "franquicia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class FranquiciaEntity {

    @Id
    private Short id;

    private String nombre;
}
