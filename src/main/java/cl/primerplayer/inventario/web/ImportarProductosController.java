package cl.primerplayer.inventario.web;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import cl.primerplayer.inventario.api.ImportarProductosUseCase;
import cl.primerplayer.inventario.api.ImportarProductosUseCase.FilaImportacion;
import cl.primerplayer.inventario.api.ImportarProductosUseCase.ResultadoImportacion;
import cl.primerplayer.inventario.infrastructure.excel.ImportarArchivoExcelService;
import cl.primerplayer.shared.api.UsuarioActual;
import cl.primerplayer.shared.api.ValidacionException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/productos")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin · Productos")
@SecurityRequirement(name = "bearer")
class ImportarProductosController {

    private final ImportarProductosUseCase importar;
    private final ImportarArchivoExcelService archivos;

    @PostMapping(value = "/importar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Importar filas de productos y stock desde JSON")
    ResponseEntity<ResultadoImportacion> importarJson(@RequestBody ImportarRequest request,
                                                       @AuthenticationPrincipal UsuarioActual usuario) {
        List<FilaImportacion> filas = request == null || request.filas() == null ? List.of() : request.filas();
        return ResponseEntity.status(HttpStatus.CREATED).body(importar.importar(filas, Map.of(), usuario.id()));
    }

    @PostMapping(value = "/importar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importar productos y stock desde un archivo XLSX o CSV")
    ResponseEntity<ResultadoImportacion> importarArchivo(@RequestPart("archivo") MultipartFile archivo,
                                                          @AuthenticationPrincipal UsuarioActual usuario) {
        if (archivo.isEmpty()) {
            throw new ValidacionException("archivo", "El archivo no puede estar vacío.");
        }
        try {
            try (InputStream entrada = archivo.getInputStream()) {
                var lectura = esCsv(archivo)
                        ? archivos.parsearCsv(entrada)
                        : archivos.parsearExcel(entrada);
                return ResponseEntity.status(HttpStatus.CREATED)
                        .body(importar.importar(lectura.filas(), lectura.erroresPorFila(), usuario.id()));
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new ValidacionException("archivo", "No se pudo leer el archivo de importación.");
        }
    }

    private static boolean esCsv(MultipartFile archivo) {
        String tipo = archivo.getContentType() == null ? "" : archivo.getContentType().toLowerCase(Locale.ROOT);
        String nombre = archivo.getOriginalFilename() == null
                ? "" : archivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (tipo.equals("text/csv") || tipo.equals("application/csv") || nombre.endsWith(".csv")) {
            return true;
        }
        if (tipo.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                || tipo.equals("application/octet-stream") || nombre.endsWith(".xlsx")) {
            return false;
        }
        throw new ValidacionException("archivo", "El formato debe ser XLSX o CSV.");
    }

    record ImportarRequest(List<FilaImportacion> filas) {}
}
