package org.idle.scheduler.auth;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.OAuth2Response;
import io.vertx.ext.auth.oauth2.impl.OAuth2API;
import io.vertx.ext.auth.oauth2.impl.OAuth2AuthProviderImpl;
import io.vertx.ext.auth.oauth2.providers.GoogleAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;

import static io.vertx.ext.auth.oauth2.impl.OAuth2API.queryToJSON;

public class AuthenticationServiceImpl implements AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationServiceImpl.class);
    private OAuth2AuthProviderImpl oauth2Provider;
    private String oauthCallbackUrl;

    public AuthenticationServiceImpl(JsonObject config) {
        final boolean deployedOnCloud = System.getenv("PORT") != null;

        JsonObject webConfig = config.getJsonObject("web");
        this.oauth2Provider = (OAuth2AuthProviderImpl) GoogleAuth.create(Vertx.currentContext().owner(),
                webConfig.getString("client_id"), webConfig.getString("client_secret"));
        if (deployedOnCloud) {
            oauthCallbackUrl = webConfig.getJsonArray("redirect_uris").getString(1);
        } else {
            oauthCallbackUrl = webConfig.getJsonArray("redirect_uris").getString(0);
        }
    }

    @Override
    public void introspectToken(String token, String tokenType, Handler<AsyncResult<JsonObject>> handler) {
        logger.info("Start introspecting");
        final JsonObject headers = new JsonObject();
        JsonObject configHeaders = oauth2Provider.getConfig().getHeaders();
        if (configHeaders != null) {
            headers.mergeIn(configHeaders);
        }
        headers.put(HttpHeaderNames.CONTENT_TYPE.toString(), HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED);
        // specify preferred accepted accessToken type
        headers.put(HttpHeaderNames.ACCEPT.toString(), "application/json,application/x-www-form-urlencoded;q=0.9");
        OAuth2API.fetch(
                oauth2Provider,
                HttpMethod.POST,
                oauth2Provider.getConfig().getIntrospectionPath() + "?" + tokenType + "=" + token,
                headers,
                Buffer.buffer(),
                res -> handleOauthApiResponse(handler, res)
        );
    }

    @Override
    public void genereteRedirectAuthorizationUrl(Handler<AsyncResult<String>> urlHandler) {
        logger.info("genereteRedirectAuthorizationUrl".toUpperCase());
        final String authorization_uri = oauth2Provider.authorizeURL(new JsonObject()
                .put("redirect_uri", oauthCallbackUrl)
                .put("scope", "openid profile email")
                .put("approval_prompt", "force")
                .put("access_type", "offline")
                .put("response_type", "code"));
        logger.info("authorization_uri: {}", authorization_uri);

        urlHandler.handle(Future.succeededFuture(authorization_uri));
    }

    @Override
    public void authenticate(String oauthFlowCode, Handler<AsyncResult<JsonObject>> resultHandler) {
        final JsonObject tokenConfig = new JsonObject()
                .put("code", oauthFlowCode)
                .put("grant_type", "authorization_code")
                .put("redirect_uri", oauthCallbackUrl);
        oauth2Provider.authenticate(tokenConfig, h -> {
            if (h.succeeded()) {
                final AccessToken user = (AccessToken) h.result();
                JsonObject principal = user.principal();
//                final String id_token = principal.getString("id_token");
//                String[] segments = id_token.split("\\.");
                logger.info("Principal: {}", principal.encodePrettily());
//                    logger.info("UserInfoEncoded: {}", Json.encodePrettily(new JsonObject(base64urlDecode(segments[1]))));
                user.userInfo(uh -> {
                    if (uh.succeeded()) {
                        JsonObject userInfo = uh.result();
                        userInfo.put("principal", principal);
                        resultHandler.handle(Future.succeededFuture(userInfo));
                    } else {
                        logger.error("UserInfo failed: ", uh.cause());
                        resultHandler.handle(Future.failedFuture(uh.cause()));
                    }
                });
            } else {
                logger.error("Authentication failed: ", h.cause());
                resultHandler.handle(Future.failedFuture(h.cause()));
            }
        });
    }

    private void handleOauthApiResponse(Handler<AsyncResult<JsonObject>> handler, AsyncResult<OAuth2Response> res) {
        if (res.failed()) {
            handler.handle(Future.failedFuture(res.cause()));
            return;
        }
        final OAuth2Response reply = res.result();

        if (reply.body() == null || reply.body().length() == 0) {
            handler.handle(Future.failedFuture("No Body"));
            return;
        }
        JsonObject json;

        if (reply.is(HttpHeaderValues.APPLICATION_JSON.toString())) {
            try {
                json = reply.jsonObject();
            } catch (RuntimeException e) {
                handler.handle(Future.failedFuture(e));
                return;
            }
        } else if (reply.is(HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED.toString()) || reply.is(HttpHeaderValues.TEXT_PLAIN.toString())) {
            try {
                json = queryToJSON(reply.body().toString());
            } catch (UnsupportedEncodingException | RuntimeException e) {
                handler.handle(Future.failedFuture(e));
                return;
            }
        } else {
            handler.handle(Future.failedFuture("Cannot handle accessToken type: " + reply.headers().get(HttpHeaderNames.CONTENT_TYPE.toString())));
            return;
        }
        try {
            if (json.containsKey("error")) {
                String description;
                Object error = json.getValue("error");
                if (error instanceof JsonObject) {
                    description = ((JsonObject) error).getString("message");
                } else {
                    // attempt to handle the error as a string
                    try {
                        description = json.getString("error_description", json.getString("error"));
                    } catch (RuntimeException e) {
                        description = error.toString();
                    }
                }
                handler.handle(Future.failedFuture(description));
            } else {
                OAuth2API.processNonStandardHeaders(json, reply, oauth2Provider.getConfig().getScopeSeparator());
                handler.handle(Future.succeededFuture(json));
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
            handler.handle(Future.failedFuture(e));
        }
    }

    //    private void refreshToken(RoutingContext ctx) {
//        logger.info("/home");
//        Cookie cookie = ctx.getCookie("rentel");
//        if (cookie != null) {
//            String decryptedCookie = cookieCipher.decryptCookie(cookie.getValue());
//            final String[] cookieParts = decryptedCookie.split(":");
//            final String userId = cookieParts[0];
//            long expiresAt = Long.parseLong(cookieParts[1]);
//            Instant expiresAtInstant = Instant.ofEpochMilli(expiresAt);
//            if (Instant.now().isAfter(expiresAtInstant)) {
//                //refresh token
//                logger.info("Start refreshing");
//                dbService.getUserByUserId(userId, userHandler -> {
//                    final List<JsonObject> result = userHandler.result();
//                    if (userHandler.succeeded() && !result.isEmpty()) {
//                        User user = new User(result.get(0));
//                        refresh(user.getRefreshToken(), rh -> {
//                            final JsonObject refreshedTokens = rh.result();
//                            logger.info("Token Refreshed: {}", Json.encodePrettily(refreshedTokens));
////                            refreshedTokens.mergeIn(user);
//                            user.setRefreshToken(refreshedTokens.getString("refreshToken"));
//                            logger.info("Merged User: {}", Json.encodePrettily(user));
//                            dbService.upsertUser(user, h -> {
//                            });
//                        });
//                    } else {
//                        //TODO redirect to login page
//                    }
//                });
//            }
//        }
//    }

//    private void refresh(String refreshToken, Handler<AsyncResult<JsonObject>> handler) {
//        final JsonObject headers = new JsonObject();
//        JsonObject configHeaders = oauth2Provider.getConfig().getHeaders();
//        if (configHeaders != null) {
//            headers.mergeIn(configHeaders);
//        }
//        final JsonObject form = new JsonObject()
//                .put("grant_type", "refresh_token")
//                .put("refresh_token", refreshToken)
//                // Salesforce does seem to require them
//                .put("client_id", oauth2Provider.getConfig().getClientID());
//        if (oauth2Provider.getConfig().getClientSecretParameterName() != null) {
//            form.put(oauth2Provider.getConfig().getClientSecretParameterName(), oauth2Provider.getConfig().getClientSecret());
//        }
//        headers.put("Content-Type", "application/x-www-form-urlencoded");
//        final Buffer payload = Buffer.buffer(stringify(form));
//        // specify preferred accepted accessToken type
//        headers.put("Accept", "application/json,application/x-www-form-urlencoded;q=0.9");
//        OAuth2API.fetch(
//                oauth2Provider,
//                HttpMethod.POST,
//                oauth2Provider.getConfig().getTokenPath(),
//                headers,
//                payload,
//                res -> handleOauthApiResponse(handler, res));
//    }
}
