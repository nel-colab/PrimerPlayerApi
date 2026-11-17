package cl.primerplayer.ventas.api;

import cl.primerplayer.shared.api.ValidacionException;

/** Canal de venta (columna {@code pedido.canal}): web (cliente registrado) o tienda (POS). */
public enum CanalPedido {
    WEB("web"), TIENDA("tienda");

    private final String valor;

    CanalPedido(String valor) {
        this.valor = valor;
    }

    /** Valor en la base y en la API. */
    public String valor() {
        return valor;
    }

    /** @throws ValidacionException si no es {@code web} ni {@code tienda} */
    public static CanalPedido de(String valor) {
        for (CanalPedido c : values()) {
            if (c.valor.equalsIgnoreCase(valor)) {
                return c;
            }
        }
        throw new ValidacionException("canal", "Use web o tienda.");
    }
}
