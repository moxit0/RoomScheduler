package org.idle.scheduler;

import co.paralleluniverse.fibers.Suspendable;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.SLF4JLogDelegateFactory;
import io.vertx.core.shareddata.LocalMap;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppLauncher {

    private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

    @Suspendable
    public static void main(String[] args) {
        System.setProperty(io.vertx.core.logging.LoggerFactory.LOGGER_DELEGATE_FACTORY_CLASS_NAME, SLF4JLogDelegateFactory.class.getName());
        final Vertx vertx = Vertx.vertx();
        //Add Jakson ObjectMapper serialization behaviour to skip null values
        Json.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        Json.prettyMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        readConfig(vertx);

        vertx.deployVerticle(MainVerticle.class.getName(), h -> {
            if (h.succeeded()) {
                logger.info("Success: {}", h.result());
            } else {
                logger.error("Something went wrong: {}", h.cause());
            }
        });
    }

    @Suspendable
    private static void readConfig(Vertx vertx) {
        try {
//            final Properties applicationProperties = new Properties();
//            applicationProperties.load(AppLauncher.class.getClassLoader().getResourceAsStream("application.properties"));
//            vertx.sharedData().getLocalMap("config").putAll(applicationProperties);

            Path configPath = Paths.get(AppLauncher.class.getClassLoader().getResource("conf/config.json").toURI());
            final List<String> readAllLines = Files.readAllLines(configPath);
            final StringBuilder configJson = new StringBuilder();
            for (int i = 0, size = readAllLines.size(); i < size; i++) {
                configJson.append(readAllLines.get(i));
            }
            JsonObject configContent = new JsonObject(configJson.toString());
            LocalMap<Object, Object> localMap = vertx.sharedData().getLocalMap("config");
//            JsonObject web = configContent.getJsonObject("web", new JsonObject());
//            JsonObject db = configContent.getJsonObject("db", new JsonObject());
//            localMap.putAll(web.getMap());
//            localMap.putAll(db.getMap());
            localMap.put("web", configContent.getJsonObject("web", new JsonObject()));
            localMap.put("db", configContent.getJsonObject("db", new JsonObject()));
            logger.info("Fetch config: {}", Json.encodePrettily(localMap));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
