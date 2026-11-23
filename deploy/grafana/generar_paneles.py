"""
Genera los paneles de Grafana de Runagard en provisioning/dashboards/runagard/*.json.

    python deploy/grafana/generar_paneles.py

Paneles propios (resumen, web, base de datos, CI/CD, logs) + tres de la comunidad adaptados a este servidor
(Node Exporter Full 1860, Spring Boot 3.x 19004, cAdvisor 14282), que se descargan de grafana.com.
Los JSON generados se versionan: Grafana los carga al arrancar (provisioning/dashboards/dashboards.yml).
"""
import json
import re
import urllib.request
from pathlib import Path

SALIDA = Path(__file__).parent / "provisioning" / "dashboards" / "runagard"
PROM = {"type": "prometheus", "uid": "prometheus"}
LOKI = {"type": "loki", "uid": "loki"}

_id = 0


def _nuevo_id():
    global _id
    _id += 1
    return _id


def t(expr, leyenda="", instant=False, ds=PROM):
    obj = {"datasource": ds, "expr": expr, "legendFormat": leyenda, "refId": chr(65 + (_nuevo_id() % 26))}
    if instant:
        obj["instant"] = True
        obj["range"] = False
    return obj


def panel(tipo, titulo, x, y, w, h, targets, unidad=None, descripcion="", umbrales=None, mappings=None,
          opciones=None, overrides=None, ds=PROM, decimales=None, minimo=None, maximo=None, custom=None):
    defaults = {"color": {"mode": "palette-classic" if tipo == "timeseries" else "thresholds"}}
    if unidad:
        defaults["unit"] = unidad
    if decimales is not None:
        defaults["decimals"] = decimales
    if minimo is not None:
        defaults["min"] = minimo
    if maximo is not None:
        defaults["max"] = maximo
    defaults["thresholds"] = {"mode": "absolute", "steps": umbrales or [{"color": "green", "value": None}]}
    if mappings:
        defaults["mappings"] = mappings
    if custom:
        defaults["custom"] = custom
    elif tipo == "timeseries":
        defaults["custom"] = {"drawStyle": "line", "lineWidth": 1, "fillOpacity": 12, "showPoints": "never",
                              "spanNulls": True}
    return {
        "id": _nuevo_id(), "type": tipo, "title": titulo, "description": descripcion, "datasource": ds,
        "gridPos": {"x": x, "y": y, "w": w, "h": h}, "targets": targets,
        "fieldConfig": {"defaults": defaults, "overrides": overrides or []},
        "options": opciones or {},
    }


def fila(titulo, y):
    return {"id": _nuevo_id(), "type": "row", "title": titulo, "collapsed": False,
            "gridPos": {"x": 0, "y": y, "w": 24, "h": 1}, "panels": []}


def ts(titulo, x, y, w, h, targets, unidad=None, descripcion="", **kw):
    return panel("timeseries", titulo, x, y, w, h, targets, unidad, descripcion,
                 opciones={"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True},
                           "tooltip": {"mode": "multi", "sort": "desc"}}, **kw)


def stat(titulo, x, y, w, h, targets, unidad=None, descripcion="", umbrales=None, mappings=None, color="value",
         **kw):
    return panel("stat", titulo, x, y, w, h, targets, unidad, descripcion, umbrales, mappings,
                 opciones={"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                           "colorMode": color, "graphMode": "area", "justifyMode": "center",
                           "textMode": "auto", "orientation": "auto"}, **kw)


def gauge(titulo, x, y, w, h, targets, descripcion=""):
    return panel("gauge", titulo, x, y, w, h, targets, "percent", descripcion,
                 umbrales=[{"color": "green", "value": None}, {"color": "orange", "value": 75},
                           {"color": "red", "value": 90}],
                 opciones={"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                           "showThresholdMarkers": True}, minimo=0, maximo=100, decimales=1)


def barras(titulo, x, y, w, h, targets, unidad, descripcion=""):
    return panel("bargauge", titulo, x, y, w, h, targets, unidad, descripcion,
                 umbrales=[{"color": "green", "value": None}],
                 opciones={"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                           "orientation": "horizontal", "displayMode": "gradient", "showUnfilled": True})


def logs(titulo, x, y, w, h, expr, descripcion=""):
    return panel("logs", titulo, x, y, w, h, [t(expr, ds=LOKI)], descripcion=descripcion, ds=LOKI,
                 opciones={"showTime": True, "wrapLogMessage": True, "sortOrder": "Descending",
                           "enableLogDetails": True, "dedupStrategy": "none"})


ESTADO = [{"type": "value", "options": {"0": {"text": "CAÍDO", "color": "red"},
                                        "1": {"text": "OK", "color": "green"}}}]
RESULTADO_BUILD = [{"type": "value", "options": {
    "0": {"text": "ÉXITO", "color": "green"}, "1": {"text": "INESTABLE", "color": "orange"},
    "2": {"text": "FALLÓ", "color": "red"}, "3": {"text": "SIN CONSTRUIR", "color": "blue"},
    "4": {"text": "ABORTADO", "color": "purple"}}}]
ROJO_SI_HAY = [{"color": "green", "value": None}, {"color": "red", "value": 1}]

# Expresiones reutilizadas
CADDY_N = "caddy_http_request_duration_seconds_count"
API_N = 'http_server_requests_seconds_count{job="api",uri!~"/actuator.*"}'
API_S = 'http_server_requests_seconds_sum{job="api",uri!~"/actuator.*"}'
DISCO = '{job="vps",mountpoint="/",fstype!~"tmpfs|overlay"}'


def tablero(uid, titulo, paneles, descripcion="", variables=None, refresco="30s", desde="now-6h", tags=None):
    return {
        "uid": uid, "title": titulo, "description": descripcion, "tags": tags or ["runagard"],
        "timezone": "America/Santiago", "refresh": refresco, "schemaVersion": 39, "version": 1,
        "editable": True, "graphTooltip": 1, "time": {"from": desde, "to": "now"},
        "templating": {"list": variables or []}, "panels": paneles,
        "links": [{"title": "Runagard", "type": "dashboards", "tags": ["runagard"], "asDropdown": True,
                   "includeVars": False, "keepTime": True}],
    }


# ---------------------------------------------------------------- Resumen
def resumen():
    p = [fila("Estado de los servicios", 0)]
    p.append(stat("Servicios caídos", 0, 1, 4, 5, [t("count(up == 0) or vector(0)", instant=True)],
                  descripcion="Fuentes de métricas que Prometheus no logra leer. Debe ser 0.",
                  umbrales=ROJO_SI_HAY, color="background"))
    p.append(stat("Servicios", 4, 1, 14, 5, [t("max by (job) (up)", "{{job}}", instant=True)],
                  descripcion="api = tienda (Spring Boot) · caddy = HTTPS/tráfico · postgres = base de datos · "
                              "minio = imágenes · jenkins = CI/CD · vps = servidor · contenedores = Docker",
                  mappings=ESTADO, color="background"))
    p.append(stat("API en marcha hace", 18, 1, 6, 5, [t('max(process_uptime_seconds{job="api"})', instant=True)],
                  "s", "Tiempo desde el último despliegue o reinicio de la API."))

    p.append(fila("Tienda: tráfico y errores", 6))
    p.append(ts("Peticiones por segundo, por dominio", 0, 7, 12, 8,
                [t(f"sum by (host) (rate({CADDY_N}[5m]))", "{{host}}")], "reqps",
                "Todo el tráfico que entra por Caddy (tienda, media y herramientas)."))
    p.append(ts("Errores del servidor (5xx)", 12, 7, 6, 8,
                [t(f'100 * sum(rate({CADDY_N}{{code=~"5.."}}[5m])) / clamp_min(sum(rate({CADDY_N}[5m])), 1e-9)',
                   "% de respuestas 5xx")], "percent", "Porcentaje de respuestas con error del servidor.",
                umbrales=[{"color": "green", "value": None}, {"color": "red", "value": 1}]))
    p.append(ts("Latencia p95 por dominio", 18, 7, 6, 8,
                [t("histogram_quantile(0.95, sum by (le, host) "
                   "(rate(caddy_http_request_duration_seconds_bucket[5m])))", "{{host}}")], "s",
                "El 95 % de las respuestas tarda menos que esto."))
    p.append(ts("API: endpoints más usados", 0, 15, 12, 8,
                [t(f"topk(10, sum by (uri) (rate({API_N}[5m])))", "{{uri}}")], "reqps"))
    p.append(ts("API: tiempo medio de respuesta por endpoint", 12, 15, 12, 8,
                [t(f"topk(10, sum by (uri) (rate({API_S}[5m])) / clamp_min(sum by (uri) (rate({API_N}[5m])), 1e-9))",
                   "{{uri}}")], "s"))
    p.append(stat("API: errores 5xx (1 h)", 0, 23, 6, 4,
                  [t('sum(increase(http_server_requests_seconds_count{job="api",status=~"5.."}[1h])) or vector(0)',
                     instant=True)], umbrales=ROJO_SI_HAY, decimales=0))
    p.append(stat("API: logs ERROR (1 h)", 6, 23, 6, 4,
                  [t('sum(increase(logback_events_total{job="api",level="error"}[1h])) or vector(0)', instant=True)],
                  umbrales=ROJO_SI_HAY, decimales=0,
                  descripcion="Líneas de log con nivel ERROR. Detalle en el panel «Logs»."))
    p.append(stat("API: logs WARN (1 h)", 12, 23, 6, 4,
                  [t('sum(increase(logback_events_total{job="api",level="warn"}[1h])) or vector(0)', instant=True)],
                  umbrales=[{"color": "green", "value": None}, {"color": "orange", "value": 20}], decimales=0))
    p.append(stat("Inicios de sesión (1 h)", 18, 23, 6, 4,
                  [t('sum(increase(http_server_requests_seconds_count{job="api",'
                     'uri=~"/api/v1/auth/(login|google)",status="200"}[1h])) or vector(0)', instant=True)],
                  decimales=0, color="none"))

    p.append(fila("Servidor (VPS 8 GB)", 27))
    p.append(gauge("CPU", 0, 28, 4, 6, [t('100 * (1 - avg(rate(node_cpu_seconds_total{job="vps",mode="idle"}[5m])))')]))
    p.append(gauge("Memoria RAM", 4, 28, 4, 6,
                   [t('100 * (1 - node_memory_MemAvailable_bytes{job="vps"} / node_memory_MemTotal_bytes{job="vps"})')]))
    p.append(gauge("Disco /", 8, 28, 4, 6,
                   [t(f"100 * (1 - max(node_filesystem_avail_bytes{DISCO}) / max(node_filesystem_size_bytes{DISCO}))")]))
    p.append(gauge("Swap", 12, 28, 4, 6,
                   [t('100 * (1 - node_memory_SwapFree_bytes{job="vps"} / clamp_min(node_memory_SwapTotal_bytes{job="vps"}, 1))')]))
    p.append(barras("Memoria por contenedor", 16, 28, 8, 12,
                    [t('sort_desc(sum by (name) (container_memory_working_set_bytes{name!=""}))', "{{name}}",
                       instant=True)], "bytes"))
    p.append(ts("CPU por contenedor", 0, 34, 16, 6,
                [t('topk(8, sum by (name) (rate(container_cpu_usage_seconds_total{name!=""}[5m])) * 100)', "{{name}}")],
                "percent", "100 % = un núcleo completo (el VPS tiene 4)."))

    p.append(fila("Base de datos y archivos", 40))
    p.append(stat("PostgreSQL", 0, 41, 4, 4, [t("max(pg_up)", instant=True)], mappings=ESTADO, color="background"))
    p.append(stat("Tamaño de la base", 4, 41, 4, 4,
                  [t('pg_database_size_bytes{datname="runagard"}', instant=True)], "bytes"))
    p.append(stat("Conexiones a la base", 8, 41, 4, 4,
                  [t('sum(pg_stat_activity_count{datname="runagard"})', instant=True)], decimales=0, color="none"))
    p.append(stat("Pool de la API en uso", 12, 41, 4, 4,
                  [t('100 * sum(hikaricp_connections_active{job="api"}) / clamp_min(sum(hikaricp_connections_max{job="api"}), 1)',
                     instant=True)], "percent",
                  umbrales=[{"color": "green", "value": None}, {"color": "orange", "value": 70}, {"color": "red", "value": 90}]))
    p.append(stat("Archivos en MinIO", 16, 41, 4, 4,
                  [t("max(minio_cluster_usage_object_total)", instant=True)], decimales=0, color="none"))
    p.append(stat("Espacio usado en MinIO", 20, 41, 4, 4,
                  [t("max(minio_cluster_usage_total_bytes)", instant=True)], "bytes", color="none"))

    p.append(fila("CI/CD", 45))
    p.append(stat("Último build", 0, 46, 12, 4,
                  [t("max by (jenkins_job) (default_jenkins_builds_last_build_result_ordinal)", "{{jenkins_job}}",
                     instant=True)], mappings=RESULTADO_BUILD, color="background"))
    p.append(stat("Duración del último build", 12, 46, 12, 4,
                  [t("max by (jenkins_job) (default_jenkins_builds_last_build_duration_milliseconds)",
                     "{{jenkins_job}}", instant=True)], "ms", color="none"))

    p.append(fila("Errores recientes en los logs", 50))
    p.append(logs("Errores y excepciones (todos los servicios)", 0, 51, 24, 10,
                  '{proyecto=~"runagard.*"} |~ "(?i)(error|exception|fatal)"'))
    return tablero("runagard-resumen", "Runagard · Resumen", p,
                   "Vista general: estado de cada servicio, tráfico, recursos, base de datos, CI/CD y errores.")


# ---------------------------------------------------------------- Web (Caddy + MinIO)
def web():
    p = [fila("Tráfico (Caddy)", 0)]
    p.append(ts("Peticiones por segundo, por dominio", 0, 1, 12, 8,
                [t(f"sum by (host) (rate({CADDY_N}[5m]))", "{{host}}")], "reqps"))
    p.append(ts("Respuestas por código HTTP", 12, 1, 12, 8,
                [t(f"sum by (code) (rate({CADDY_N}[5m]))", "{{code}}")], "reqps"))
    p.append(ts("Latencia por dominio (p50 / p95 / p99)", 0, 9, 12, 8,
                [t(f"histogram_quantile({q}, sum by (le, host) (rate(caddy_http_request_duration_seconds_bucket[5m])))",
                   f"{{{{host}}}} p{int(q * 100)}") for q in (0.5, 0.95, 0.99)], "s"))
    p.append(ts("Datos enviados por dominio", 12, 9, 12, 8,
                [t("sum by (host) (rate(caddy_http_response_size_bytes_sum[5m]))", "{{host}}")], "Bps"))
    p.append(ts("Peticiones en curso", 0, 17, 8, 7,
                [t("sum by (server) (caddy_http_requests_in_flight)", "{{server}}")], "short"))
    p.append(stat("Errores 4xx (1 h)", 8, 17, 4, 7,
                  [t(f'sum(increase({CADDY_N}{{code=~"4.."}}[1h])) or vector(0)', instant=True)], decimales=0,
                  umbrales=[{"color": "green", "value": None}, {"color": "orange", "value": 100}]))
    p.append(stat("Errores 5xx (1 h)", 12, 17, 4, 7,
                  [t(f'sum(increase({CADDY_N}{{code=~"5.."}}[1h])) or vector(0)', instant=True)], decimales=0,
                  umbrales=ROJO_SI_HAY))
    p.append(stat("Última recarga de Caddy", 16, 17, 8, 7,
                  [t("max(caddy_config_last_reload_success_timestamp_seconds) * 1000", instant=True)], "dateTimeFromNow",
                  color="none"))

    p.append(fila("Archivos (MinIO)", 24))
    p.append(stat("Objetos guardados", 0, 25, 6, 5, [t("max(minio_cluster_usage_object_total)", instant=True)],
                  decimales=0, color="none"))
    p.append(stat("Espacio usado", 6, 25, 6, 5, [t("max(minio_cluster_usage_total_bytes)", instant=True)], "bytes",
                  color="none"))
    p.append(gauge("Disco de MinIO usado", 12, 25, 6, 5,
                   [t("100 * (1 - max(minio_cluster_capacity_usable_free_bytes) / max(minio_cluster_capacity_usable_total_bytes))")]))
    p.append(ts("Tráfico de MinIO", 18, 25, 6, 5,
                [t("sum(rate(minio_s3_traffic_sent_bytes[5m]))", "enviado"),
                 t("sum(rate(minio_s3_traffic_received_bytes[5m]))", "recibido")], "Bps"))
    return tablero("runagard-web", "Runagard · Web y archivos", p,
                   "Caddy (HTTPS, tráfico por dominio, códigos y latencia) y MinIO (imágenes y comprobantes).")


# ---------------------------------------------------------------- PostgreSQL
def postgres():
    db = 'datname="runagard"'
    p = [fila("PostgreSQL de la tienda", 0)]
    p.append(stat("Estado", 0, 1, 4, 4, [t("max(pg_up)", instant=True)], mappings=ESTADO, color="background"))
    p.append(stat("En marcha desde", 4, 1, 5, 4, [t("max(pg_postmaster_start_time_seconds) * 1000", instant=True)],
                  "dateTimeFromNow", color="none"))
    p.append(stat("Tamaño", 9, 1, 5, 4, [t(f"pg_database_size_bytes{{{db}}}", instant=True)], "bytes", color="none"))
    p.append(stat("Aciertos de caché", 14, 1, 5, 4,
                  [t(f"100 * sum(rate(pg_stat_database_blks_hit{{{db}}}[30m])) / clamp_min(sum(rate(pg_stat_database_blks_hit{{{db}}}[30m])) + sum(rate(pg_stat_database_blks_read{{{db}}}[30m])), 1e-9)",
                     instant=True)], "percent",
                  umbrales=[{"color": "red", "value": None}, {"color": "orange", "value": 90}, {"color": "green", "value": 98}],
                  descripcion="Lecturas servidas desde memoria. Bajo 90 % sostenido = falta memoria compartida."))
    p.append(stat("Deadlocks (24 h)", 19, 1, 5, 4,
                  [t(f"sum(increase(pg_stat_database_deadlocks{{{db}}}[24h])) or vector(0)", instant=True)],
                  umbrales=ROJO_SI_HAY, decimales=0))
    p.append(ts("Conexiones por estado", 0, 5, 12, 8,
                [t(f"sum by (state) (pg_stat_activity_count{{{db}}})", "{{state}}")], "short"))
    p.append(ts("Transacciones por segundo", 12, 5, 12, 8,
                [t(f"sum(rate(pg_stat_database_xact_commit{{{db}}}[5m]))", "commit"),
                 t(f"sum(rate(pg_stat_database_xact_rollback{{{db}}}[5m]))", "rollback")], "ops"))
    p.append(ts("Filas por segundo", 0, 13, 12, 8,
                [t(f"sum(rate(pg_stat_database_tup_{op}{{{db}}}[5m]))", nombre)
                 for op, nombre in (("fetched", "leídas"), ("inserted", "insertadas"), ("updated", "actualizadas"),
                                    ("deleted", "borradas"))], "rowsps"))
    p.append(ts("Transacción más larga en curso", 12, 13, 12, 8,
                [t(f"max(pg_stat_activity_max_tx_duration{{{db}}})", "segundos")], "s",
                "Una transacción abierta por minutos bloquea a otras: revisar logs de la API.",
                umbrales=[{"color": "green", "value": None}, {"color": "red", "value": 60}]))
    p.append(fila("Pool de conexiones de la API (HikariCP)", 21))
    p.append(ts("Conexiones del pool", 0, 22, 12, 8,
                [t(f'sum(hikaricp_connections_{e}{{job="api"}})', e) for e in ("active", "idle", "pending", "max")],
                "short"))
    p.append(ts("Espera para obtener conexión", 12, 22, 12, 8,
                [t('sum(rate(hikaricp_connections_acquire_seconds_sum{job="api"}[5m])) / clamp_min(sum(rate(hikaricp_connections_acquire_seconds_count{job="api"}[5m])), 1e-9)',
                   "promedio")], "s"))
    return tablero("runagard-postgres", "Runagard · PostgreSQL", p,
                   "Base de datos de la tienda (postgres-exporter) y el pool de conexiones de la API.")


# ---------------------------------------------------------------- CI/CD
def cicd():
    jj = "jenkins_job"
    p = [fila("Pipelines de Jenkins", 0)]
    p.append(stat("Último build", 0, 1, 12, 5,
                  [t(f"max by ({jj}) (default_jenkins_builds_last_build_result_ordinal)", f"{{{{{jj}}}}}", instant=True)],
                  mappings=RESULTADO_BUILD, color="background"))
    p.append(stat("Último build hace", 12, 1, 12, 5,
                  [t(f"max by ({jj}) (default_jenkins_builds_last_build_start_time_milliseconds)", f"{{{{{jj}}}}}",
                     instant=True)], "dateTimeFromNow", color="none"))
    p.append(stat("Builds exitosos", 0, 6, 6, 4,
                  [t("sum(default_jenkins_builds_success_build_count_total)", instant=True)], decimales=0, color="none"))
    p.append(stat("Builds fallidos", 6, 6, 6, 4,
                  [t("sum(default_jenkins_builds_failed_build_count_total)", instant=True)], decimales=0,
                  umbrales=[{"color": "green", "value": None}, {"color": "orange", "value": 1}]))
    p.append(stat("Builds en cola", 12, 6, 6, 4, [t("max(jenkins_queue_size_value) or vector(0)", instant=True)],
                  decimales=0, color="none"))
    p.append(stat("Ejecutores en uso", 18, 6, 6, 4, [t("max(jenkins_executor_in_use_value)", instant=True)],
                  decimales=0, color="none"))
    p.append(ts("Duración del último build", 0, 10, 12, 8,
                [t(f"max by ({jj}) (default_jenkins_builds_last_build_duration_milliseconds) / 1000", f"{{{{{jj}}}}}")],
                "s"))
    p.append(ts("Pruebas fallando en el último build", 12, 10, 12, 8,
                [t(f"max by ({jj}) (default_jenkins_builds_last_build_tests_failing)", f"{{{{{jj}}}}}")], "short"))
    p.append(fila("Recursos de las herramientas", 18))
    p.append(ts("Memoria: Jenkins y SonarQube", 0, 19, 12, 8,
                [t('sum by (name) (container_memory_working_set_bytes{name=~"runagard-ops-(jenkins|sonarqube|sonar-db)-1"})',
                   "{{name}}")], "bytes", "Topes: Jenkins 3 GB, SonarQube 2,5 GB, sonar-db 384 MB."))
    p.append(ts("CPU: Jenkins y SonarQube", 12, 19, 12, 8,
                [t('sum by (name) (rate(container_cpu_usage_seconds_total{name=~"runagard-ops-(jenkins|sonarqube|sonar-db)-1"}[5m])) * 100',
                   "{{name}}")], "percent"))
    p.append(logs("Logs de Jenkins y SonarQube", 0, 27, 24, 10, '{servicio=~"jenkins|sonarqube"}'))
    return tablero("runagard-cicd", "Runagard · CI/CD", p,
                   "Resultado y duración de los pipelines (runagard-api, runagard-front) y consumo de Jenkins/SonarQube.")


# ---------------------------------------------------------------- Logs
def logs_tablero():
    variables = [
        {"name": "servicio", "label": "Servicio", "type": "query", "datasource": LOKI,
         "query": {"label": "servicio", "refId": "LokiVariableQueryEditor-VariableQuery", "stream": "", "type": 1},
         "definition": "label_values(servicio)", "includeAll": True, "multi": True, "allValue": ".+",
         "current": {"selected": True, "text": ["All"], "value": ["$__all"]}, "refresh": 2, "sort": 1},
        {"name": "buscar", "label": "Contiene", "type": "textbox", "query": "",
         "current": {"text": "", "value": ""}},
    ]
    p = [ts("Líneas de log por servicio", 0, 0, 24, 7,
            [t('sum by (servicio) (count_over_time({servicio=~"$servicio"} |~ "(?i)$buscar" [$__auto]))', "{{servicio}}",
               ds=LOKI)], "short", ds=LOKI,
            custom={"drawStyle": "bars", "fillOpacity": 70, "stacking": {"mode": "normal"}, "lineWidth": 0}),
         logs("Logs", 0, 7, 24, 22, '{servicio=~"$servicio"} |~ "(?i)$buscar"')]
    return tablero("runagard-logs", "Runagard · Logs", p,
                   "Logs de todos los contenedores (Loki). Filtra por servicio y por texto.", variables, desde="now-1h")


# ---------------------------------------------------------------- Comunidad (adaptados)
def comunidad(gnet_id, uid, titulo, ajustar=None):
    url = f"https://grafana.com/api/dashboards/{gnet_id}/revisions/latest/download"
    texto = urllib.request.urlopen(url, timeout=60).read().decode("utf-8")
    texto = texto.replace('"uid": "${DS_PROMETHEUS}"', '"uid": "prometheus"')
    texto = texto.replace('"datasource": "${DS_PROMETHEUS}"', '"datasource": {"type": "prometheus", "uid": "prometheus"}')
    texto = texto.replace("${DS_PROMETHEUS}", "prometheus")
    d = json.loads(texto)
    for k in ("__inputs", "__requires", "__elements", "id"):
        d.pop(k, None)
    d.update(uid=uid, title=titulo, timezone="America/Santiago", gnetId=gnet_id)
    d["tags"] = sorted(set(d.get("tags", [])) | {"runagard"})
    if ajustar:
        d = ajustar(d)
    return d


def ajustar_spring(d):
    # Sin Kubernetes: no hay etiqueta "namespace". Se quita la variable y sus filtros.
    d["templating"]["list"] = [v for v in d["templating"]["list"] if v.get("name") != "Namespace"]
    texto = json.dumps(d, ensure_ascii=False)
    texto = re.sub(r',\s*namespace=\\"\$Namespace\\"', "", texto)
    texto = re.sub(r'namespace=\\"\$Namespace\\"\s*,\s*', "", texto)
    d = json.loads(texto)
    for v in d["templating"]["list"]:
        if v.get("name") == "application":
            v["current"] = {"selected": True, "text": "primer-player-api", "value": "primer-player-api"}
    return d


def ajustar_node(d):
    for v in d.get("templating", {}).get("list", []):
        if v.get("name") == "ds_prometheus":
            v["current"] = {"selected": True, "text": "Prometheus", "value": "prometheus"}
        if v.get("name") == "job":
            v["current"] = {"selected": True, "text": "vps", "value": "vps"}
    return d


def main():
    SALIDA.mkdir(parents=True, exist_ok=True)
    tableros = {
        "01-resumen.json": resumen(),
        "02-web.json": web(),
        "03-api.json": comunidad(19004, "runagard-api", "Runagard · API (Spring Boot)", ajustar_spring),
        "04-postgres.json": postgres(),
        "05-servidor.json": comunidad(1860, "runagard-servidor", "Runagard · Servidor (VPS)", ajustar_node),
        "06-contenedores.json": comunidad(14282, "runagard-contenedores", "Runagard · Contenedores"),
        "07-cicd.json": cicd(),
        "08-logs.json": logs_tablero(),
    }
    for nombre, d in tableros.items():
        (SALIDA / nombre).write_text(json.dumps(d, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        print(f"{nombre}: {d['title']}")


if __name__ == "__main__":
    main()
