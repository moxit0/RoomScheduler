package org.idle.scheduler.auth;

import co.paralleluniverse.fibers.Suspendable;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.oauth2.AccessToken;
import io.vertx.ext.auth.oauth2.OAuth2Response;
import io.vertx.ext.auth.oauth2.impl.OAuth2API;
import static io.vertx.ext.auth.oauth2.impl.OAuth2API.queryToJSON;
import io.vertx.ext.auth.oauth2.impl.OAuth2AuthProviderImpl;
import io.vertx.ext.auth.oauth2.providers.GoogleAuth;
import io.vertx.ext.sync.Sync;
import static io.vertx.ext.sync.Sync.awaitResult;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Idle
 */
public final class AuthenticationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationService.class);
    private final OAuth2AuthProviderImpl oauth2Provider;
    private final String oauthCallbackUrl;

    private AuthenticationService() {
        JsonObject webConfig = (JsonObject) Vertx.currentContext().owner().sharedData().getLocalMap("config").get("web");
        logger.info("Web config: {}", webConfig.encodePrettily());
        this.oauth2Provider = (OAuth2AuthProviderImpl) GoogleAuth.create(Vertx.currentContext().owner(),
                webConfig.getString("client_id"), webConfig.getString("client_secret"));
        final boolean deployedOnCloud = System.getenv("PORT") != null;
        if (deployedOnCloud) {
            oauthCallbackUrl = webConfig.getJsonArray("redirect_uris").getString(1);
        } else {
            oauthCallbackUrl = webConfig.getJsonArray("redirect_uris").getString(0);
        }
    }

    @Suspendable
    public static AuthenticationService getInstance() {
        return AuthenticationServiceHolder.INSTANCE;
    }

    private static class AuthenticationServiceHolder {
        private static final AuthenticationService INSTANCE = new AuthenticationService();
    }

    @Suspendable
    public String genereteRedirectAuthorizationUrl() {
        logger.info("genereteRedirectAuthorizationUrl".toUpperCase());
        final String authorization_uri = oauth2Provider.authorizeURL(new JsonObject()
                .put("redirect_uri", oauthCallbackUrl)
                .put("scope", "openid profile email")
                .put("approval_prompt", "force")
                .put("access_type", "offline")
                .put("response_type", "code"));

        return authorization_uri;
    }

    @Suspendable
    public JsonObject authenticate(String oauthFlowCode) {
        final JsonObject tokenConfig = new JsonObject()
                .put("code", oauthFlowCode)
                .put("grant_type", "authorization_code")
                .put("redirect_uri", oauthCallbackUrl);
        final User user = awaitResult(h -> oauth2Provider.authenticate(tokenConfig, h));
        JsonObject principal = user.principal();
//                final String id_token = principal.getString("id_token");
//                String[] segments = id_token.split("\\.");
        logger.info("Principal: {}", principal.encodePrettily());
//                    logger.info("UserInfoEncoded: {}", Json.encodePrettily(new JsonObject(base64urlDecode(segments[1]))));
        JsonObject userInfo = awaitResult(((AccessToken)user)::userInfo);
        userInfo.put("principal", principal);
        return userInfo;
//        resultHandler.handle(Future.succeededFuture(userInfo));
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
}
