/**
 * Módulo media: imágenes públicas (productos, eventos) y comprobantes privados (transferencias) en S3/MinIO, con su
 * registro en {@code archivo}. La subida a S3 ocurre FUERA de la transacción (regla 4); después se registra la fila.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Media",
    allowedDependencies = { "shared" }
)
package cl.primerplayer.media;
