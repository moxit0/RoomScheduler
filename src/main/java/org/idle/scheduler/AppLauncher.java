package org.idle.scheduler;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import org.idle.scheduler.auth.AuthenticationVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppLauncher extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    public static void main(String[] args) {
        System.setProperty(io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME, SLF4JLogDelegateFactory.class.getName());
        final Vertx vertx = Vertx.vertx();
        //Add Jakson ObjectMapper serialization behaviour to skip null values
        Json.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        Json.prettyMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
//        readConfig(vertx);

        vertx.deployVerticle(MainVerticle.class.getName(), h -> {
            if (h.succeeded()) {
                logger.info("MainVerticle done: {}", h.result());
            } else {
                logger.error("Something went wrong: {}", h.cause());
            }
        });
        vertx.deployVerticle(AuthenticationVerticle.class.getName(), auth -> {
            if (auth.succeeded()) {
                logger.info("AuthenticationVerticle done: {}", auth.result());
            } else {
                logger.error("AuthenticationVerticle failed deployment: ", auth.cause());
            }
        });
    }

    @Override
    public void start(Future<Void> startFuture) {
        System.setProperty(io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME, SLF4JLogDelegateFactory.class.getName());
        final Vertx vertx = Vertx.vertx();
        //Add Jakson ObjectMapper serialization behaviour to skip null values
        Json.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        Json.prettyMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
//        readConfig(vertx);

        vertx.deployVerticle(MainVerticle.class.getName(), h -> {
            if (h.succeeded()) {
                logger.info("MainVerticle done: {}", h.result());
            } else {
                logger.error("Something went wrong: {}", h.cause());
            }
        });
        vertx.deployVerticle(AuthenticationVerticle.class.getName(), auth -> {
            if (auth.succeeded()) {
                logger.info("AuthenticationVerticle done: {}", auth.result());
            } else {
                logger.error("AuthenticationVerticle failed deployment: ", auth.cause());
            }
        });
    }
//    private static void readConfig(Vertx vertx) {
//        try {
////            final Properties applicationProperties = new Properties();
////            applicationProperties.load(AppLauncher.class.getClassLoader().getResourceAsStream("application.properties"));
////            vertx.sharedData().getLocalMap("config").putAll(applicationProperties);
//
//            Path configPath = Paths.get(AppLauncher.class.getClassLoader().getResource("conf/config.json").toURI());
//            final List<String> readAllLines = Files.readAllLines(configPath);
//            final StringBuilder configJson = new StringBuilder();
//            for (int i = 0, size = readAllLines.size(); i < size; i++) {
//                configJson.append(readAllLines.get(i));
//            }
//            System.out.println("Fetch config: \n" + Json.encodePrettily(configJson));
//
//            JsonObject configContent = new JsonObject(configJson.toString());
//            LocalMap<Object, Object> localMap = vertx.sharedData().getLocalMap("config");
////            JsonObject web = configContent.getJsonObject("web", new JsonObject());
////            JsonObject db = configContent.getJsonObject("db", new JsonObject());
////            localMap.putAll(web.getMap());
////            localMap.putAll(db.getMap());
//            localMap.put("web", configContent.getJsonObject("web", new JsonObject()));
//            localMap.put("db", configContent.getJsonObject("db", new JsonObject()));
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

}
