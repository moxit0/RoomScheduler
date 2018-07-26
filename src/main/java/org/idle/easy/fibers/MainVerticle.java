package org.idle.easy.fibers;

import co.paralleluniverse.fibers.Suspendable;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.providers.GoogleAuth;
import io.vertx.ext.sync.Sync;
import io.vertx.ext.sync.SyncVerticle;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.vertx.ext.sync.Sync.awaitResult;

import io.vertx.redis.RedisClient;


public class MainVerticle extends SyncVerticle {

    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
    private static final String COLLECTION_NAME = "Entities";
    private static final String DB_URL = "https://d139e57f-9b16-4c30-9e71-579bbf66993f-bluemix.cloudant.com";
    private WebClient webClient;
    //    private MongoClient mongoClient;
    private RedisClient redisClient;
    private CookieCipher cookieCipher;

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
        router.route().handler(CookieHandler.create());
        router.route().handler(BodyHandler.create());

/*      String clientId = "873521963386-08elodg1nfpu2j784bikm66e3hjf4m3p.apps.googleusercontent.com";
        String clientSecret = "sbYikW3olRC0O4SqvSD3xQrA";
        OAuth2Auth oauth2Provider = GoogleAuth.create(vertx, clientId, clientSecret);
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().handler(UserSessionHandler.create(oauth2Provider));
        AuthHandler redirectAuthHandler = RedirectAuthHandler.create(oauth2Provider);
        router.route("/*").handler(redirectAuthHandler);
        router.post("/login").handler(FormLoginHandler.create(oauth2Provider));
         */
        router.route().failureHandler(ErrorHandler.create());
//        router.routeWithRegex("/roomScheduler/api\\/.*").handler(Sync.fiberHandler(this::authenticate));
        router.get("/roomScheduler/api/getWebContent").handler(Sync.fiberHandler(this::getWebContent));
        router.get("/roomScheduler/api/entities").handler(Sync.fiberHandler(this::getAllEntities));
//        router.get("/roomScheduler/api/entities/:id").handler(Sync.fiberHandler(this::getEntityById));
        router.put("/roomScheduler/api/entities").handler(Sync.fiberHandler(this::saveNewEntity));
        router.get("/roomScheduler/api/googleauth").handler(Sync.fiberHandler(this::startGoogleAuth));
        router.get("/roomScheduler/api/googletoken").handler(Sync.fiberHandler(this::getGoogleToken));
        router.route("/roomScheduler/*").handler(StaticHandler.create()
                .setIndexPage("index.html").setCachingEnabled(false));
        // HttpServer will be automatically shared if port matches
        server.requestHandler(router::accept).listen(8080);
        webClient = WebClient.create(vertx, new WebClientOptions().setSsl(true));
//        mongoClient = MongoClient.createShared(vertx, new JsonObject().put("connection_string", "mongodb://127.0.0.1:27017/testDb"));
//        redisClient = RedisClient.create(vertx);
        cookieCipher = new CookieCipher();
    }

    @Suspendable
    private void saveNewEntity(RoutingContext routingContext) {
        String userId = routingContext.get("userId");
        final JsonObject entry = routingContext.getBodyAsJson();
        entry.put("userId", userId);
        JsonObject scheduledRoom = entry.getJsonObject("scheduledRoom");
        Instant startDate = scheduledRoom.getInstant("startDate");
        Instant endDate = scheduledRoom.getInstant("endDate");
        scheduledRoom.put("startDate", startDate).put("endDate", endDate);
        Long ts = startDate.toEpochMilli();
        long result = awaitResult(h -> redisClient.zadd("user:entry:" + userId, ts, entry.encodePrettily(), h));
//        logger.info("saveEntity: {},\n result: {}", entry.encodePrettily(), result);

        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(entry.encode());
    }

    @Suspendable
    private void getAllEntities(RoutingContext routingContext) {
        String userId = routingContext.get("userId");
//        JsonArray entries = new JsonArray();
        JsonArray entries = awaitResult(h -> redisClient.zrange("user:entry:" + userId, 0, -1, h));
        JsonArray entities = new JsonArray(entries.stream().map(e -> new JsonObject(e.toString())).collect(Collectors.toList()));
        logger.info("getAllEntities: key:{} \n{}", "user:entry:" + userId, Json.encodePrettily(entities));
        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(entities.encodePrettily());
    }

//    @Suspendable
//    private void getEntityById(RoutingContext routingContext) {
//        final JsonObject query = new JsonObject()
//                .put("_id", routingContext.pathParam("id"));
//        final List<JsonObject> entity = awaitResult(h -> mongoClient.find(COLLECTION_NAME, query, h));
//        routingContext.response()
//                .end(Json.encodePrettily(entity));
//    }

    @Suspendable
    private void getWebContent(RoutingContext routingContext) {
        HttpRequest<Buffer> httpRequest = doDataBaseGet("/roomcheduler/28e0e33d90130fd469f2a2d2028122d0").get();
        HttpResponse<Buffer> response = awaitResult(httpRequest::send);
        routingContext
                .response()
//                .response().putHeader("Location", "/roomScheduler/")
//                .setStatusCode(302)
                .setStatusCode(200)
                .end(response.bodyAsString());
    }

    @Suspendable
    private void authenticate(RoutingContext routingContext) {
        Cookie cookie = routingContext.getCookie("roomScheduler");
        String requestPath = routingContext.request().path();
        if (cookie == null && !"/roomScheduler/api/googleauth".equals(requestPath) && !"/roomScheduler/api/googletoken".equals(requestPath)) {
            routingContext.response().putHeader("Location", "/index.html")
                    .setStatusCode(301)
                    .end();
        } else {
            if (cookie != null) {
                String decryptedCookie = cookieCipher.decryptCookie(cookie.getValue());
                routingContext.put("userId", decryptedCookie.split(":")[0]);
                logger.info("decryptedCookie: {} ", decryptedCookie);
            }
            routingContext.next();
        }
    }

    @Suspendable
    private void startGoogleAuth(RoutingContext routingContext) {
        String clientId = "873521963386-08elodg1nfpu2j784bikm66e3hjf4m3p.apps.googleusercontent.com";
        String clientSecret = "sbYikW3olRC0O4SqvSD3xQrA";

        OAuth2Auth oauth2 = GoogleAuth.create(vertx, clientId, clientSecret);

        final String callbackUrl = "http://localhost:8080/roomScheduler/api/googletoken";
        // Authorization oauth2 URI
        String authorization_uri = oauth2.authorizeURL(new JsonObject()
                .put("redirect_uri", callbackUrl)
                .put("scope", "openid profile email https://www.googleapis.com/auth/calendar")
                .put("approval_prompt", "force")
                .put("access_type", "offline")
                .put("state", UUID.randomUUID().toString()));

        routingContext.response()
                .putHeader("Location", authorization_uri)
                .setStatusCode(302)
                .end();
    }

    @Suspendable
    private void getGoogleToken(RoutingContext routingContext) {
        String clientId = "873521963386-08elodg1nfpu2j784bikm66e3hjf4m3p.apps.googleusercontent.com";
        String clientSecret = "sbYikW3olRC0O4SqvSD3xQrA";

        OAuth2Auth oauth2Provider = GoogleAuth.create(vertx, clientId, clientSecret);
        final String callbackUrl = "http://localhost:8080/roomScheduler/api/googletoken";

        final JsonObject tokenConfig = new JsonObject()
                .put("code", routingContext.request().getParam("code"))
                .put("redirect_uri", callbackUrl);
        try {
            final User user = awaitResult(h -> oauth2Provider.authenticate(tokenConfig, h));
            final JsonObject principal = user.principal();
//        String uri = "https://www.googleapis.com/oauth2/v1/userinfo?v=2&oauth_token=" + token.principal().getValue("access_token");
            String uri = "/oauth2/v1/userinfo?v=2&oauth_token=" + principal.getValue("access_token");
            HttpResponse<Buffer> response = awaitResult(h -> webClient.get(443, "www.googleapis.com", uri).ssl(true).send(h));
            JsonObject userInfo = response.bodyAsJsonObject();
            logger.info("AccessToken: {}", Json.encodePrettily(principal));
            logger.info("userInfo: {}", Json.encodePrettily(userInfo));
            final String userId = userInfo.getString("id");
            Long p = awaitResult(h -> redisClient.hset("user:" + userId, "principal", principal.encodePrettily(), h));
            Long u = awaitResult(h -> redisClient.hset("user:" + userId, "userInfo", userInfo.encodePrettily(), h));

            Cookie cookie = createCookie(userId, principal.getLong("expires_at").toString(), principal.getString("access_token"));
            routingContext.addCookie(cookie);
            routingContext.response().putHeader("Location", "/roomScheduler/")
                    .setStatusCode(301)
                    .end();
        } catch (Exception e) {
            logger.error("Grumna:", e);
            e.printStackTrace();
            routingContext.response()
                    .setStatusCode(500)
                    .end("Boom !!!");
        }
    }

    @Suspendable
    private Cookie createCookie(String userId, String expiresAt, String accessToken) {
        final String cookieSource = String.join(":", userId, expiresAt, accessToken);
        String encryptedCookie = cookieCipher.encryptCookie(cookieSource);
        Cookie cookie = Cookie.cookie("roomScheduler", encryptedCookie);
        cookie.setMaxAge(31536000000L / 1000);
        cookie.setPath("/roomScheduler/");
        cookie.setHttpOnly(false);
        return cookie;
    }

    @Suspendable
    private Supplier<HttpRequest<Buffer>> doDataBaseGet(String requestPath) {
        //"/roomcheduler/28e0e33d90130fd469f2a2d2028122d0"
        return () -> webClient.getAbs(DB_URL + requestPath)
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
                .putHeader(HttpHeaders.AUTHORIZATION.toString(), "Basic ZDEzOWU1N2YtOWIxNi00YzMwLTllNzEtNTc5YmJmNjY5OTNmLWJsdWVtaXg6NjQwZmM3OGJlZjhlZDY3ZGEyZTQzM2ZkNjBmMTg5ZjFiMDU3ZjUxZmE4NDUwZWZiNGNmM2ViNjNkMThlYzliMg==");
    }
}
