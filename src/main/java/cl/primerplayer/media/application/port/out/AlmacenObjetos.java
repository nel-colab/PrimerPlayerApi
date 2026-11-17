package cl.primerplayer.media.application.port.out;

import java.time.Duration;

/** Puerto de salida: almacenamiento de objetos (S3/MinIO). Llamadas HTTP: nunca dentro de una transacción. */
public interface AlmacenObjetos {

    /** @throws cl.primerplayer.media.api.AlmacenNoDisponibleException si no responde */
    void guardar(String bucket, String clave, byte[] contenido, String mime);

    String urlPublica(String bucket, String clave);

    String urlFirmada(String bucket, String clave, Duration vigencia);
}
