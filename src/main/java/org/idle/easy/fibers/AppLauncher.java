package org.idle.easy.fibers;

import io.vertx.core.Vertx;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppLauncher {
    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    public static void main(String[] args) {
        System.setProperty(io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME, SLF4JLogDelegateFactory.class.getName());

        Vertx.vertx().deployVerticle(MainVerticle.class.getName(), h -> {
            if (h.succeeded()) {
                logger.info("Success: {}", h.result());
            } else {
                logger.error("Something went wrong: {}", h.cause());
            }
        });
    }
}
