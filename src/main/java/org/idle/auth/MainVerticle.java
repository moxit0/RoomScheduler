package org.idle.auth;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.sync.Sync;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ErrorHandler;


/**
 * Created by idle on 6/14/2017.
 */
public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        HttpServer server = vertx.createHttpServer();
        PersistenceDispatcher persistenceDispatcher = new PersistenceDispatcher();
        Router router = Router.router(vertx);
        // enable BodyHandler globally for easiness of body accessing
        router.route().handler(BodyHandler.create()).failureHandler(ErrorHandler.create());

        // test api: dev playground
//        router.route(HttpMethod.GET, "/test").handler(persistenceDispatcher::getEntity);
        router.route(HttpMethod.GET, "/test").handler(Sync.fiberHandler(persistenceDispatcher::getEntity));
        // HttpServer will be automatically shared if port matches
        server.requestHandler(router::accept).listen(8089);
    }
}
