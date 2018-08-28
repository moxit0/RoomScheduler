package org.idle.scheduler.auth;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@ProxyGen
public interface AuthenticationService {

    static AuthenticationService createProxy(Vertx vertx, String address) {
        return new AuthenticationServiceVertxEBProxy(vertx, address);
    }

    /**
     *
     * @param token Access or Id token
     * @param tokenType could be access_token or id_token
     * @param handler callback for handling of response
     */
    void introspectToken(String token, String tokenType, Handler<AsyncResult<JsonObject>> handler);

    void genereteRedirectAuthorizationUrl(Handler<AsyncResult<String>> urlHandler);

    void authenticate(String oauthFlowCode, Handler<AsyncResult<JsonObject>> resultHandler);
}
