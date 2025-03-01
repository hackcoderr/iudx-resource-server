package iudx.resource.server.databroker.listeners;

import static iudx.resource.server.common.Constants.UNIQUE_ATTR_Q;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import iudx.resource.server.cache.CacheService;
import iudx.resource.server.cache.cacheImpl.CacheType;
import iudx.resource.server.common.BroadcastEventType;

public class UniqueAttribQListener implements RMQListeners {

  private static final Logger LOGGER = LogManager.getLogger(UniqueAttribQListener.class);

  private final RabbitMQClient client;
  private final CacheService cache;

  private final QueueOptions options =
      new QueueOptions()
          .setMaxInternalQueueSize(1000)
          .setKeepMostRecent(true);

  public UniqueAttribQListener(RabbitMQClient client, CacheService cache) {
    this.client = client;
    this.cache = cache;
  }

  @Override
  public void start() {
    client
        .start()
        .onSuccess(handler -> {
          LOGGER.trace("starting Q listener for unique-attributes");
          client.basicConsumer(UNIQUE_ATTR_Q, options, rmqConsumer -> {
            if (rmqConsumer.succeeded()) {
              RabbitMQConsumer mqConsumer = rmqConsumer.result();
              mqConsumer.handler(message -> {
                Buffer body = message.body();
                if (body != null) {
                  JsonObject uniqueAttribJson = new JsonObject(body);
                  LOGGER.debug("received message from unique-attrib Q :" + uniqueAttribJson);
                  String key = uniqueAttribJson.getString("id");
                  String value = uniqueAttribJson.getString("unique-attribute");
                  String eventType = uniqueAttribJson.getString("eventType");
                  BroadcastEventType event = BroadcastEventType.from(eventType);
                  LOGGER.debug(event);
                  JsonObject cacheJson = new JsonObject();
                  cacheJson.put("type", CacheType.UNIQUE_ATTRIBUTE);

                  if (event == null) {
                    LOGGER.error("Invalid BroadcastEventType [ null ] ");
                    return;
                  }

                  if (event.equals(BroadcastEventType.CREATE)) {
                    // pass key and value only for create event, for others update,delete cache will
                    // fetch from DB.
                    cacheJson.put("key", key);
                    cacheJson.put("value", value);
                  }

                  cache.refresh(cacheJson, cacheHandler -> {
                    if (cacheHandler.succeeded()) {
                      LOGGER.debug("unique attrib message published to Cache Verticle");
                    } else {
                      LOGGER.debug("unique attrib message published to Cache Verticle fail");
                    }
                  });
                } else {
                  LOGGER.error("Empty json received from revoke_token queue");
                }
              });
            }
          });
        })
        .onFailure(handler -> {
          LOGGER.error("Rabbit client startup failed.");
        });



  }

}
