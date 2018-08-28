package org.idle.scheduler;

import co.paralleluniverse.fibers.Suspendable;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
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
import org.idle.scheduler.auth.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

import static io.vertx.ext.sync.Sync.awaitResult;

public class MainVerticle extends SyncVerticle {

    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
    //    private static final String DB_URL = "https://d139e57f-9b16-4c30-9e71-579bbf66993f-bluemix.cloudant.com";
//    private static final String BASIC_AUTH_HEADER = "Basic ZDEzOWU1N2YtOWIxNi00YzMwLTllNzEtNTc5YmJmNjY5OTNmLWJsdWVtaXg6NjQwZmM3OGJlZjhlZDY3ZGEyZTQzM2ZkNjBmMTg5ZjFiMDU3ZjUxZmE4NDUwZWZiNGNmM2ViNjNkMThlYzliMg==";
    private WebClient webClient;
    //    private MongoClient mongoClient;
//    private RedisClient redisClient;
    private CookieCipher cookieCipher;

    @Override
    @Suspendable
    public void start() {
//        final ConfigRetriever retriever = ConfigRetriever.create(vertx);
//        retriever.getConfig(h -> {
//            if (h.succeeded()) {
//                JsonObject result = h.result();
//                config().mergeIn(result.getJsonObject("web"));
        JsonObject webConfig = (JsonObject) vertx.sharedData().getLocalMap("config").get("web");
        int httpPort;
        try {
            //check if some cloud cpecific environment is present
            boolean deployedOnCloud = System.getenv("PORT") != null;
            if (deployedOnCloud) {
                httpPort = Integer.parseInt(System.getenv("PORT"));
            } else {
                httpPort = webConfig.getInteger("httpPort");
            }
            logger.info("Deployed with port: {}", httpPort);

        } catch (Exception e) {
            httpPort = 8080;
            logger.warn("Environment variable PORT not found or not valid. Defautling to: {}", httpPort);
        }
        Router router = Router.router(vertx);
        router.route().handler(CorsHandler.create("*")
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.PUT)
                .allowedMethod(HttpMethod.DELETE)
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString())
                .allowedHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString())
                .allowedHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString())
                .allowedHeader(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString())
                .allowedHeader(HttpHeaderNames.AUTHORIZATION.toString())
                .allowedHeader(HttpHeaderNames.CONTENT_TYPE.toString())
                .allowedHeader(HttpHeaderNames.CACHE_CONTROL.toString()));
        router.route().failureHandler(ErrorHandler.create(true));
        router.route().handler(CookieHandler.create());
        router.route().handler(BodyHandler.create());
        router.route().consumes(HttpHeaderValues.APPLICATION_JSON.toString());
        router.route().produces(HttpHeaderValues.APPLICATION_JSON.toString());


        router.route().failureHandler(ErrorHandler.create());
        router.get("/signin").handler(Sync.fiberHandler(this::startGoogleAuth));
//        router.get("/auth/callback").handler(Sync.fiberHandler(this::getGoogleToken));
//        router.routeWithRegex("/room-scheduler/api\\/.*").handler(Sync.fiberHandler(this::authenticate));
        router.routeWithRegex("/room-scheduler/api\\/.*").handler(Sync.fiberHandler(this::verifyAuth));
        router.get("/room-scheduler/api/getWebContent").handler(Sync.fiberHandler(this::getWebContent));
        router.get("/room-scheduler/api/events").handler(Sync.fiberHandler(this::getAllEntities));
//        router.get("/room-scheduler/api/entities/:id").handler(Sync.fiberHandler(this::getEntityById));
        router.post("/room-scheduler/api/events").handler(Sync.fiberHandler(this::saveNewEntity));
        router.get("/room-scheduler/api/googletoken").handler(Sync.fiberHandler(this::getGoogleToken));
//        router.route("/*").handler(ctx -> ctx.response().sendFile("webroot/index.html"));
        router.route("/*").handler(StaticHandler.create()
                .setIndexPage("index.html").setCachingEnabled(false));

        // HttpServer will be automatically shared if port matches
        HttpServer server = vertx.createHttpServer();
        server.requestHandler(router::accept).listen(httpPort);
        webClient = WebClient.create(vertx, new WebClientOptions().setSsl(true));
        cookieCipher = new CookieCipher();

//                JsonObject userInfo = awaitResult(uh -> authenticationService.authenticate("", fiberHandler(uh)));
//                logger.info("UserInfo: {}", Json.encodePrettily(userInfo));
//            } else {
//                logger.error(" ConfigRetriever failed: {}", h.cause());
//            }
//        });
    }

    @Suspendable
    private void saveNewEntity(RoutingContext routingContext) {
//        String userId = "102661047872421691075";
        String userId = routingContext.get("userId");
        final JsonObject entry = routingContext.getBodyAsJson();
        entry.put("userId", userId);
//        JsonObject scheduledRoom = entry.getJsonObject("scheduledRoom");
//        Instant startDate = scheduledRoom.getInstant("startDate");
//        Instant endDate = scheduledRoom.getInstant("endDate");
//        scheduledRoom.put("startDate", startDate).put("endDate", endDate);

//        Long ts = startDate.toEpochMilli();
//        long result = awaitResult(h -> redisClient.zadd("user:entry:" + userId, ts, entry.encodePrettily(), h));
        JsonObject docs = doDataBasePost("/roomcheduler", entry);
        logger.info("saveEntity: {},\n result: {}", entry.encodePrettily(), Json.encodePrettily(docs));

        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(entry.encode());
    }

    @Suspendable
    private void getAllEntities(RoutingContext routingContext) {
        String userId = routingContext.get("userId");
//        String userId = "102661047872421691075";
        JsonObject queryBody = new JsonObject().put("selector", new JsonObject().put("userId", userId));
        logger.info("queryBody: {}", queryBody.encodePrettily());

        JsonObject dbResponse = doDataBasePost("/roomcheduler/_find", queryBody);
        JsonArray docs = dbResponse.getJsonArray("docs", new JsonArray());
        logger.info("getAllEntities: {}", docs.encodePrettily());
        routingContext.response().putHeader(HttpHeaders.CONTENT_TYPE, "application/json").end(docs.encode());
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
//        HttpRequest<Buffer> httpRequest = doDataBaseGet("/roomcheduler/28e0e33d90130fd469f2a2d2028122d0").get();
//        HttpResponse<Buffer> response = awaitResult(httpRequest::send);
        routingContext
                //                .response()
                .response().putHeader("Location", "/")
                .setStatusCode(302)
                //                .setStatusCode(200)
                .end();
    }

    @Suspendable
    private void authenticate(RoutingContext routingContext) {
        Cookie cookie = routingContext.getCookie("room-scheduler");
        String requestPath = routingContext.request().path();
        if (cookie == null && !"/room-scheduler/api/googleauth".equals(requestPath) && !"/room-scheduler/api/googletoken".equals(requestPath)) {
//            routingContext.response().putHeader("Location", "/")
////                    .setStatusCode(301)
////                    .end();
            redirectToHome(routingContext);
        } else {
            if (cookie != null) {
                String decryptedCookie = cookieCipher.decryptCookie(cookie.getValue());
                routingContext.put("userId", decryptedCookie.split(":")[0]);
                logger.info("encryptedCookie: {} ", cookie.getValue());
                logger.info("decryptedCookie: {} ", decryptedCookie);
            }
            routingContext.next();
        }
    }

    @Suspendable
    private void startGoogleAuth(RoutingContext routingContext) {
        try {
            logger.info("startGoogleAuth".toUpperCase());
            final String authorizationUri = AuthenticationService.getInstance().genereteRedirectAuthorizationUrl();
            logger.info("Auth authorization_uri: {}", authorizationUri);
            Cookie oldCookie = routingContext.removeCookie("room-scheduler");
            if (oldCookie != null)
                oldCookie.setMaxAge(0L);

            routingContext.response()
                    .putHeader("Location", authorizationUri)
                    .setStatusCode(302)
                    .end();
        }catch (Throwable e){
            logger.error("EEEE:", e);
        }
    }

    @Suspendable
    private void getGoogleToken(RoutingContext routingContext) {
        logger.info("getGoogleToken".toUpperCase());
        JsonObject userInfo = AuthenticationService.getInstance().authenticate(routingContext.request().getParam("code"));
        JsonObject principal = userInfo.getJsonObject("principal");
        final String userId = userInfo.getString("sub");
        final long expiresAt = principal.getLong("expires_at", 0L);
        logger.info("UserInfo: {}", Json.encodePrettily(userInfo));
        Cookie cookie = createCookie(userId, Long.toString(expiresAt), principal.getString("access_token"));
        routingContext.addCookie(cookie);

        redirectToHome(routingContext);
    }

    @Suspendable
    private Cookie createCookie(String userId, String expiresAt, String accessToken) {
        final String cookieSource = String.join(":", userId, expiresAt, accessToken);
        String encryptedCookie = cookieCipher.encryptCookie(cookieSource);
        Cookie cookie = Cookie.cookie("room-scheduler", encryptedCookie);
        cookie.setMaxAge(31536000000L / 1000);
        cookie.setPath("/");
        cookie.setHttpOnly(false);
        return cookie;
    }

    @Suspendable
    private Supplier<HttpRequest<Buffer>> doDataBaseGet(String requestPath) {
        //"/roomcheduler/28e0e33d90130fd469f2a2d2028122d0"
        JsonObject dbConfig = (JsonObject) vertx.sharedData().getLocalMap("config").get("db");
        return () -> webClient.getAbs("https://" + dbConfig.getString("host") + requestPath)
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
                .putHeader(HttpHeaders.AUTHORIZATION.toString(), dbConfig.getString("auth_header"))
                .ssl(true);
    }

    @Suspendable
    private JsonObject doDataBasePost(String requestPath, JsonObject requestBody) {
        //"/roomcheduler/28e0e33d90130fd469f2a2d2028122d0"
        JsonObject dbConfig = (JsonObject) vertx.sharedData().getLocalMap("config").get("db");
        final HttpRequest<Buffer> httpRequest = webClient.postAbs("https://" + dbConfig.getString("host") + requestPath)
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
                .putHeader(HttpHeaders.AUTHORIZATION.toString(), dbConfig.getString("auth_header"))
                .ssl(true);
        HttpResponse<Buffer> response = awaitResult(h -> httpRequest.sendJsonObject(requestBody, h));
        return response.bodyAsJsonObject();
    }

    @Suspendable
    private void redirectToHome(RoutingContext routingContext) {
        routingContext.response().putHeader(HttpHeaderNames.LOCATION, "/")
                .setStatusCode(301)
                .end();
    }

    @Suspendable
    private void apiNotAuthorized(RoutingContext ctx, String msg) {
        ctx.response()
                .setStatusMessage(HttpResponseStatus.UNAUTHORIZED.reasonPhrase())
                .setStatusCode(HttpResponseStatus.UNAUTHORIZED.code())
                .end(new JsonObject()
                        .put("success", false)
                        .put("error", msg).encode());
    }

    @Suspendable
    private void verifyAuth(RoutingContext ctx) {
        String authHeader = ctx.request().getHeader("Authorization");
        if (authHeader != null) {
            authHeader = authHeader.replaceFirst("Bearer ", "");
            String decryptedCookie = cookieCipher.decryptCookie(authHeader);
            ctx.put("userId", decryptedCookie.split(":")[0]);
            logger.info("encryptedCookie: {} ", authHeader);
            logger.info("decryptedCookie: {} ", decryptedCookie);
//            logger.info("AuthHeader: {}", authHeader);
//            String[] decodedAuthSegments = decodeAuthHeader(authHeader);
            ctx.next();
        } else {
            apiNotAuthorized(ctx, "Not authorized");
        }
    }

    private static String base64urlDecode(String str) {
        return new String(Base64.getUrlDecoder().decode(str.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    private static String base64urlEncode(String str) {
        return new String(Base64.getUrlEncoder().encode(str.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    private String[] decodeAuthHeader(String header) {
        String firstDecode = base64urlDecode(header);
        String[] segments = firstDecode.split(":");
        String[] decodedSegments = new String[segments.length];
        for (int i = 0; i < segments.length; i++) {
            decodedSegments[i] = base64urlDecode(segments[i]);
        }
        return decodedSegments;
    }

    private JsonArray decodeAuthToken(String token) {
        final String[] segments = token.split("\\.");
        final List<String> decodedSegments = new ArrayList<>(segments.length);
        for (int i = 0; i < segments.length; i++) {
            decodedSegments.add(base64urlDecode(segments[i]));
        }
        return new JsonArray(decodedSegments);
    }
}
