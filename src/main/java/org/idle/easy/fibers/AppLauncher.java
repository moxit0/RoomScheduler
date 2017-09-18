package org.idle.easy.fibers;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class AppLauncher {
    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(MainVerticle.class.getName(), h -> {
            if (h.succeeded()) {
                logger.info("Success: {0}", h.result());
            } else {
                logger.error("Something went wrong: {0}", h.cause());
            }
        });
    }
}
