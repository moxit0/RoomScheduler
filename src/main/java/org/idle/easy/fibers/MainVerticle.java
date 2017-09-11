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
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.providers.GoogleAuth;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.sync.Sync;
import io.vertx.ext.sync.SyncVerticle;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.ErrorHandler;

import java.util.List;
import java.util.UUID;

import static io.vertx.ext.sync.Sync.awaitResult;


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
        router.route().handler(CorsHandler.create("*")
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedHeader("Access-Control-Request-Method")
                .allowedHeader("Access-Control-Allow-Credentials")
                .allowedHeader("Access-Control-Allow-Origin")
                .allowedHeader("Access-Control-Allow-Headers")
                .allowedHeader("Content-Type"));
        // enable BodyHandler globally for easiness of body accessing
        router.route().handler(BodyHandler.create()).failureHandler(ErrorHandler.create());
        router.route(HttpMethod.GET, "/getWebContent").handler(Sync.fiberHandler(this::getWebContent));
        router.route(HttpMethod.GET, "/entities").handler(Sync.fiberHandler(this::getAllEntities));
        router.route(HttpMethod.GET, "/entities/:id").handler(Sync.fiberHandler(this::getEntityById));
        router.route(HttpMethod.PUT, "/entities").handler(Sync.fiberHandler(this::saveNewEntity));
        router.route(HttpMethod.GET, "/googleauth").handler(Sync.fiberHandler(this::startGoogleAuth));
        router.route(HttpMethod.GET, "/googletoken").handler(Sync.fiberHandler(this::getGoogleToken));
        // HttpServer will be automatically shared if port matches
        server.requestHandler(router::accept).listen(8088);
        webClient = WebClient.create(vertx, new WebClientOptions().setSsl(true));
//        mongoClient = MongoClient.createShared(vertx, new JsonObject().put("connection_string", "mongodb://127.0.0.1:27017/testDb"));
    }

    @Suspendable
    private void saveNewEntity(RoutingContext routingContext){
        final String response = awaitResult(h ->  mongoClient.save(COLLECTION_NAME, routingContext.getBodyAsJson(), h));
        routingContext.response().end(response);
    }

    @Suspendable
    private void getAllEntities(RoutingContext routingContext){
//        final List<JsonObject> entities = Sync.awaitResult(h ->  mongoClient.find(COLLECTION_NAME, new JsonObject(), h));
        final String item = "{\"calendarId\":\"#mycal\",\"title\":\"opaa\",\"scheduledRoom\":{\"id\":2,\"number\":1,\"type\":\"DOUBLE\",\"startDate\":\"2017-09-03T21:00:00.000Z\",\"endDate\":\"2017-09-03T21:00:00.000Z\",\"allDay\":true,\"agendaData\":null},\"backgroundColor\":\"#333333\",\"foregroundColor\":\"#ffffff\"}";
        logger.info(item);
        routingContext.response().end(Json.encodePrettily(item));
    }

    @Suspendable
    private void getEntityById(RoutingContext routingContext){
        final JsonObject query = new JsonObject()
                .put("_id", routingContext.pathParam("id"));
        final List<JsonObject> entity = awaitResult(h ->  mongoClient.find(COLLECTION_NAME, query, h));
        routingContext.response()
                .end(Json.encodePrettily(entity));
    }

    @Suspendable
    private void getWebContent(RoutingContext routingContext){
        final HttpResponse<Buffer> response = awaitResult(h -> webClient.getAbs("https://www.google.com").send(h));
        final String responseContent = response.bodyAsString("UTF-8");
        logger.info("Result of Http request: {0}", responseContent);
        routingContext.response()
                .putHeader(HttpHeaderNames.CONTENT_TYPE, "text/html")
                .end(responseContent);
    }

    @Suspendable
    public void startGoogleAuth(RoutingContext routingContext) {
        String clientId = "873521963386-08elodg1nfpu2j784bikm66e3hjf4m3p.apps.googleusercontent.com";
        String clientSecret = "sbYikW3olRC0O4SqvSD3xQrA";

        OAuth2Auth oauth2 = GoogleAuth.create(vertx, clientId, clientSecret);

        final String callbackUrl = "http://localhost:8088/googletoken";
        // Authorization oauth2 URI
        String authorization_uri = oauth2.authorizeURL(new JsonObject()
                .put("redirect_uri", callbackUrl)
//                .put("scope", "email")
                .put("scope", "openid profile email https://www.googleapis.com/auth/calendar")
//                .put("state", "3(#0/!~"));
                .put("approval_prompt","force")
                .put("access_type","offline")
                .put("state", UUID.randomUUID().toString()));

        routingContext.response()
                .putHeader("Location", authorization_uri)
                .setStatusCode(302)
                .end();
    }

    @Suspendable
    public void getGoogleToken(RoutingContext routingContext) {
        String clientId = "873521963386-08elodg1nfpu2j784bikm66e3hjf4m3p.apps.googleusercontent.com";
        String clientSecret = "sbYikW3olRC0O4SqvSD3xQrA";

        OAuth2Auth oauth2Provider = GoogleAuth.create(vertx, clientId, clientSecret);
        final String callbackUrl = "http://localhost:8088/googletoken";

        final JsonObject tokenConfig = new JsonObject()
                .put("code", routingContext.request().getParam("code"))
                .put("redirect_uri", callbackUrl);

        final AccessToken token = awaitResult(h -> oauth2Provider.getToken(tokenConfig, h));

        logger.info("AccessToken: {0}", Json.encodePrettily(token.principal()));
        if (!token.principal().containsKey("refresh_token")) {
//            try {
//                Void revokeResult = awaitResult(h -> token.revoke("access_token", h));
//                logger.info("Revoke succeed: {}", revokeResult.toString());
//            } catch (Exception e) {
//                logger.error("Revoke failed", e);
//            try {
//                Void logoutResult = awaitResult(token::logout);
//                logger.info("Logout succeed: {0}", logoutResult.toString());
//            } catch (Exception ex) {
//                logger.error("Logout failed: {0}", ex);
//            }
//            }
        }
        routingContext.response()
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(token.principal()));


//        oauth2Provider.getToken(tokenConfig, res -> {
//            if (res.failed()) {
//                logger.error("Access Token Error: ", res.cause());
//                response
//                        .putHeader("content-type", "application/json; charset=utf-8")
//                        .end(Json.encodePrettily(res.cause()));
//            } else {
//                // Get the access token object (the authorization code is given from the previous step).
//                AccessToken token = res.result();
//                logger.info("Google AccessToken: {}", token.principal());
//                response
//                        .putHeader("content-type", "application/json; charset=utf-8")
//                        .end(Json.encodePrettily(token));
//            }
//        });
    }
}
