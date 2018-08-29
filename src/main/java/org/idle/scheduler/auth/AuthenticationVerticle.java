package org.idle.scheduler.auth;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.auth.oauth2.impl.OAuth2AuthProviderImpl;
import io.vertx.ext.auth.oauth2.providers.GoogleAuth;
import io.vertx.serviceproxy.ServiceBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticationVerticle extends AbstractVerticle {
    public static final String EB_ADDRESS = "auth-queue";
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationVerticle.class);

    @Override
    public void start(Future<Void> startFuture) {
        ConfigRetriever retriever = ConfigRetriever.create(vertx);
        retriever.getConfig(ch -> {
            if (ch.succeeded()) {
                config().mergeIn(ch.result());
                try {
                    new ServiceBinder(vertx)
                            .setAddress(EB_ADDRESS)
                            .register(AuthenticationService.class, new AuthenticationServiceImpl(config()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                startFuture.complete();
            } else {
                logger.error(" ConfigRetriever failed: {}", ch.cause());
                startFuture.fail(ch.cause());
            }
        });
    }
}