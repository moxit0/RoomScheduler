package org.idle.auth;

import co.paralleluniverse.fibers.Suspendable;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.sync.Sync;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;


public final class PersistenceDispatcher {

    @Suspendable
    public void getEntity(RoutingContext routingContext){
        WebClient webClient = WebClient.create(Vertx.currentContext().owner(),
                new WebClientOptions().setSsl(true).setUserAgent("vert-x3"));
        HttpResponse<Buffer> response = Sync.awaitResult(h -> webClient.getAbs("https://www.google.bg/?gfe_rd=cr&ei=aOlGWZS_CK3s8weUyImoDQ").send(h));

        routingContext.response()
                .putHeader(HttpHeaderNames.CONTENT_TYPE, "text/html")
                .end(response.bodyAsString());
    }
}
