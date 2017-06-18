package org.idle.auth;

import io.vertx.core.Vertx;

/**
 * Created by idle on 6/14/2017.
 */
public class Launcher {

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new MainVerticle(), h -> {
            if (h.succeeded()) {
                System.out.println("Success: " + h.result());
            } else {
                h.cause().printStackTrace();
            }
        });
    }
}
