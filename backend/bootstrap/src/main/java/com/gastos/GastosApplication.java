package com.gastos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Unico punto de arranque del monolito modular. Todos los contextos acotados viajan
 * en el mismo proceso y comparten memoria: un solo JVM que cabe holgadamente en las
 * instancias de 256-512 MB de las plataformas gratuitas.
 */
@SpringBootApplication
public class GastosApplication {

    public static void main(String[] args) {
        SpringApplication.run(GastosApplication.class, args);
    }
}
