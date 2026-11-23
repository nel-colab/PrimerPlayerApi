package cl.primerplayer.shared.web;

/** Títulos legibles para cada {@code code} de error. */
public final class Titulos {

    private Titulos() {}

    public static String de(String code) {
        return switch (code) {
            case "validation" -> "Solicitud inválida";
            case "not-found" -> "Recurso no encontrado";
            case "method-not-allowed" -> "Método no permitido";
            case "unsupported-media-type" -> "Tipo de contenido no soportado";
            case "not-acceptable" -> "Formato no disponible";
            case "payload-too-large" -> "Archivo demasiado grande";
            case "unauthorized" -> "No autenticado";
            case "forbidden" -> "Acceso denegado";
            case "account-locked" -> "Cuenta bloqueada temporalmente";
            case "email-taken" -> "Correo ya registrado";
            case "email-not-verified" -> "Correo no verificado";
            case "rate-limited" -> "Demasiadas solicitudes";
            case "out-of-stock" -> "Sin stock";
            case "stock-limit" -> "Stock insuficiente";
            case "purchase-limit" -> "Límite de compra superado";
            case "item-unavailable" -> "Producto no disponible";
            case "barcode-taken" -> "Código de barras en uso";
            case "barcode-immutable" -> "Código de barras inmutable";
            case "version-conflict" -> "Conflicto de versión";
            case "limit-admin-only" -> "Solo un administrador puede cambiar el límite";
            case "precondition-required" -> "Falta la versión (If-Match)";
            case "event-full" -> "Evento sin cupos";
            case "event-past" -> "El evento ya ocurrió";
            case "slot-taken" -> "Horario ocupado";
            case "request-not-pending" -> "La solicitud ya fue resuelta";
            case "order-invalid-state" -> "Cambio de estado del pedido no permitido";
            case "empty" -> "Carrito vacío";
            case "invalid-quantity" -> "Cantidad inválida";
            case "idempotency-conflict" -> "Clave de idempotencia reutilizada";
            case "payment-provider-error" -> "Webpay no está disponible";
            case "registration-not-cancellable" -> "La inscripción no se puede cancelar";
            case "payments-mismatch" -> "Los pagos no cuadran con el total";
            case "receipt-required" -> "Falta el número de boleta";
            case "transfer-proof-required" -> "Falta el comprobante de transferencia";
            case "tcg-card-required" -> "Faltan los datos de la carta";
            case "cash-change-mismatch" -> "El vuelto no coincide con el calculado";
            case "storage-unavailable" -> "Almacenamiento de archivos no disponible";
            case "invalid-token" -> "Enlace inválido o vencido";
            case "already-verified" -> "El correo ya está verificado";
            case "account-banned" -> "Cuenta suspendida";
            case "blacklisted" -> "Cuenta sin permiso para comprar";
            case "self-action" -> "No puedes aplicarte esta acción";
            case "last-admin" -> "Debe quedar al menos un administrador";
            case "profile-incomplete" -> "Información personal incompleta";
            case "invalid-google-token" -> "Google no confirmó tu identidad";
            case "google-unavailable" -> "Google no está disponible";
            case "google-email-mismatch" -> "La cuenta de Google tiene otro correo";
            case "google-account-in-use" -> "Cuenta de Google ya vinculada";
            default -> "Error de negocio";
        };
    }
}
