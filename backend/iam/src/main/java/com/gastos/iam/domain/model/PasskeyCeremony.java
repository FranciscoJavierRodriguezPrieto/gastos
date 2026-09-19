package com.gastos.iam.domain.model;

/**
 * Para que se emitio un reto.
 *
 * <p>Se guarda junto al reto y se comprueba al consumirlo. Sin esta distincion, un reto
 * pedido para dar de alta una passkey —que se obtiene con la sesion ya iniciada— podria
 * presentarse en el endpoint de acceso, que es publico.</p>
 */
public enum PasskeyCeremony {

    /** Alta de una passkey nueva, siempre con sesion iniciada. */
    REGISTRO,

    /** Acceso con una passkey ya registrada, sin sesion. */
    ACCESO
}
