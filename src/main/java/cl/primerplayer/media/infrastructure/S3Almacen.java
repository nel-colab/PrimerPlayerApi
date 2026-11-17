package cl.primerplayer.media.infrastructure;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import cl.primerplayer.media.api.AlmacenNoDisponibleException;
import cl.primerplayer.media.application.port.out.AlmacenObjetos;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * Adaptador S3 (MinIO en local, cualquier S3 compatible en producción). Al arrancar crea los buckets si faltan y deja
 * {@code public-media} con lectura pública; si el almacenamiento no está disponible, solo avisa (la API arranca igual).
 */
@Component
@EnableConfigurationProperties(S3Almacen.Props.class)
@Slf4j
class S3Almacen implements AlmacenObjetos, ApplicationRunner {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final Props props;

    S3Almacen(Props props) {
        this.props = props;
        var credenciales = StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey()));
        var config = S3Configuration.builder().pathStyleAccessEnabled(true).build();   // MinIO: http://host/bucket/clave
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(credenciales)
                .serviceConfiguration(config)
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(20)))
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(props.endpoint()))
                .region(Region.of(props.region()))
                .credentialsProvider(credenciales)
                .serviceConfiguration(config)
                .build();
    }

    @Override
    public void guardar(String bucket, String clave, byte[] contenido, String mime) {
        try {
            s3.putObject(b -> b.bucket(bucket).key(clave).contentType(mime).contentLength((long) contenido.length),
                    RequestBody.fromBytes(contenido));
        } catch (SdkException e) {
            log.warn("No se pudo guardar el archivo en el bucket {}: {}", bucket, e.getMessage());
            throw new AlmacenNoDisponibleException();
        }
    }

    @Override
    public String urlPublica(String bucket, String clave) {
        return props.urlPublica().replaceAll("/+$", "") + "/" + bucket + "/" + clave;
    }

    @Override
    public String urlFirmada(String bucket, String clave, Duration vigencia) {
        return presigner.presignGetObject(p -> p.signatureDuration(vigencia)
                .getObjectRequest(g -> g.bucket(bucket).key(clave))).url().toString();
    }

    /** Crea los buckets si faltan. Idempotente; no detiene la API si el almacenamiento aún no está arriba. */
    @Override
    public void run(ApplicationArguments args) {
        try {
            asegurarBucket("private-docs", false);
            asegurarBucket("public-media", true);
        } catch (SdkException e) {
            log.warn("Almacenamiento de archivos no disponible al arrancar ({}): las subidas fallarán hasta que responda.",
                    e.getMessage());
        }
    }

    private void asegurarBucket(String bucket, boolean lecturaPublica) {
        try {
            s3.headBucket(b -> b.bucket(bucket));
        } catch (NoSuchBucketException e) {
            s3.createBucket(b -> b.bucket(bucket));
            log.info("Bucket {} creado", bucket);
        }
        if (lecturaPublica) {
            s3.putBucketPolicy(b -> b.bucket(bucket).policy("""
                    {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},\
                    "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}""".formatted(bucket)));
        }
    }

    /**
     * {@code pp.media.*}.
     *
     * @param urlPublica base pública para las imágenes ({@code <urlPublica>/<bucket>/<clave>})
     */
    @ConfigurationProperties(prefix = "pp.media")
    record Props(String endpoint, String region, String accessKey, String secretKey, String urlPublica) {}
}
