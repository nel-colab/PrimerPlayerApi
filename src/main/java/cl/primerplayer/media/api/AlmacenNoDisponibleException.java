package cl.primerplayer.media.api;

import org.springframework.http.HttpStatus;

import cl.primerplayer.shared.api.ExcepcionNegocio;

/** 503 {@code storage-unavailable}: el almacenamiento de archivos (S3/MinIO) no respondió. Nada quedó registrado. */
public class AlmacenNoDisponibleException extends ExcepcionNegocio {

    public AlmacenNoDisponibleException() {
        super("storage-unavailable", HttpStatus.SERVICE_UNAVAILABLE,
                "No pudimos guardar el archivo. Inténtalo nuevamente en unos minutos.");
    }
}
