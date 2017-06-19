package org.idle.easy.fibers;

import co.paralleluniverse.fibers.Suspendable;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.sync.Sync;
import io.vertx.ext.sync.SyncVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.ErrorHandler;

import java.util.List;


public class MainVerticle extends SyncVerticle {

    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
    private static final String COLLECTION_NAME= "Entities";
    private WebClient webClient;
    private MongoClient mongoClient;

    @Override
    @Suspendable
    public void start(Future<Void> startFuture) throws Exception {
        super.start(startFuture);
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);
        // enable BodyHandler globally for easiness of body accessing
        router.route().handler(BodyHandler.create()).failureHandler(ErrorHandler.create());
        router.route(HttpMethod.GET, "/getWebContent").handler(Sync.fiberHandler(this::getWebContent));
        router.route(HttpMethod.GET, "/entities").handler(Sync.fiberHandler(this::getAllEntities));
        router.route(HttpMethod.GET, "/entities/:id").handler(Sync.fiberHandler(this::getEntityById));
        router.route(HttpMethod.PUT, "/entities").handler(Sync.fiberHandler(this::saveNewEntity));
        // HttpServer will be automatically shared if port matches
        server.requestHandler(router::accept).listen(8089);
        webClient = WebClient.create(vertx, new WebClientOptions().setSsl(true));
        mongoClient = MongoClient.createShared(vertx, new JsonObject().put("connection_string", "mongodb://127.0.0.1:27017/testDb"));
    }

    @Suspendable
    private void saveNewEntity(RoutingContext routingContext){
        final String response = Sync.awaitResult(h ->  mongoClient.save(COLLECTION_NAME, routingContext.getBodyAsJson(), h));
        routingContext.response().end(response);
    }

    @Suspendable
    private void getAllEntities(RoutingContext routingContext){
        final List<JsonObject> entities = Sync.awaitResult(h ->  mongoClient.find(COLLECTION_NAME, new JsonObject(), h));
        routingContext.response().end(Json.encodePrettily(entities));
    }

    @Suspendable
    private void getEntityById(RoutingContext routingContext){
        final JsonObject query = new JsonObject()
                .put("_id", routingContext.pathParam("id"));
        final List<JsonObject> entity = Sync.awaitResult(h ->  mongoClient.find(COLLECTION_NAME, query, h));
        routingContext.response()
                .end(Json.encodePrettily(entity));
    }

    @Suspendable
    private void getWebContent(RoutingContext routingContext){
        final HttpResponse<Buffer> response = Sync.awaitResult(h -> webClient.getAbs("https://www.google.com").send(h));
        final String responseContent = response.bodyAsString("UTF-8");
        logger.info("Result of Http request: {0}", responseContent);
        routingContext.response()
                .putHeader(HttpHeaderNames.CONTENT_TYPE, "text/html")
                .end(responseContent);
    }
}
