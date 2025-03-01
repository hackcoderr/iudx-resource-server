package iudx.resource.server.apiserver.subscription;

import static iudx.resource.server.apiserver.util.Constants.APPEND_SUB_SQL;
import static iudx.resource.server.apiserver.util.Constants.CREATE_SUB_SQL;
import static iudx.resource.server.apiserver.util.Constants.DELETE_SUB_SQL;
import static iudx.resource.server.apiserver.util.Constants.JSON_DETAIL;
import static iudx.resource.server.apiserver.util.Constants.JSON_TITLE;
import static iudx.resource.server.apiserver.util.Constants.JSON_TYPE;
import static iudx.resource.server.apiserver.util.Constants.SUBSCRIPTION_ID;
import static iudx.resource.server.apiserver.util.Constants.SUB_TYPE;
import static iudx.resource.server.apiserver.util.Constants.UPDATE_SUB_SQL;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.resource.server.apiserver.response.ResponseType;
import iudx.resource.server.database.postgres.PostgresService;
import iudx.resource.server.databroker.DataBrokerService;

/**
 * class contains all method for operations on subscriptions.
 *
 */
public class SubscriptionService {

  private static final Logger LOGGER = LogManager.getLogger(SubscriptionService.class);

  Subscription subscription = null;

  /**
   * get the context of subscription according to the type passed in message body.
   * 
   * @param type type of subscription either <strong>streaming</strong> or <strong>callback</strong>
   * @param databroker databroker verticle object
   * @param databaseService database verticle object
   * @return an object of Subscription class
   */
  private Subscription getSubscriptionContext(SubsType type, DataBrokerService databroker,
      PostgresService pgService) {
    LOGGER.info("getSubscriptionContext() method started");
    if (type != null && type.equals(SubsType.CALLBACK)) {
      LOGGER.info("callback subscription context");
      return CallbackSubscription.getInstance(databroker, pgService);
    } else {
      LOGGER.info("streaming subscription context");
      return StreamingSubscription.getInstance(databroker, pgService);
    }
  }

  /**
   * create a subscription.
   * 
   * @param json subscription json
   * @param databroker databroker verticle object
   * @param databaseService database verticle object
   * @return a future of JsonObject
   */
  public Future<JsonObject> createSubscription(JsonObject json, DataBrokerService databroker,
      PostgresService pgService, JsonObject authInfo) {
    LOGGER.info("createSubscription() method started");
    Promise<JsonObject> promise = Promise.promise();
    SubsType subType = SubsType.valueOf(json.getString(SUB_TYPE));
    subscription = getSubscriptionContext(subType, databroker, pgService);
    assertNotNull(subscription);
    subscription.create(json).onComplete(handler -> {
      if (handler.succeeded()) {
        JsonObject brokerSubResult = handler.result();

        StringBuilder query = new StringBuilder(CREATE_SUB_SQL
            .replace("$1", brokerSubResult.getString("id"))
            .replace("$2", subType.type)
            .replace("$3", brokerSubResult.getString("id"))
            .replace("$4", json.getJsonArray("entities").getString(0))
            .replace("$5", authInfo.getString("expiry")));

        pgService.executeQuery(query.toString(), pgHandler -> {
          if (pgHandler.succeeded()) {
            promise.complete(brokerSubResult);
          } else {
            // TODO : rollback mechanism in case of pg error [to unbind/delete created sub]
            JsonObject res = new JsonObject(pgHandler.cause().getMessage());
            promise.fail(generateResponse(res).toString());
          }
        });

      } else {
        JsonObject res = new JsonObject(handler.cause().getMessage());
        promise.fail(generateResponse(res).toString());
      }
    });
    return promise.future();
  }

  /**
   * update an existing subscription.
   * 
   * @param json subscription json
   * @param databroker databroker verticle object
   * @param databaseService database verticle object
   * @return a future of josbObject
   */
  public Future<JsonObject> updateSubscription(JsonObject json, DataBrokerService databroker,
      PostgresService pgService, JsonObject authInfo) {
    LOGGER.info("updateSubscription() method started");
    Promise<JsonObject> promise = Promise.promise();

    StringBuilder query = new StringBuilder(UPDATE_SUB_SQL
        .replace("$1", authInfo.getString("expiry"))
        .replace("$2", json.getString(SUBSCRIPTION_ID))
        .replace("$3", json.getJsonArray("entities").getString(0)));

    LOGGER.debug(query);
    pgService.executeQuery(query.toString(), pgHandler -> {
      if (pgHandler.succeeded()) {
        JsonObject response = new JsonObject();
        JsonArray entities = new JsonArray();
        entities.add(json.getJsonArray("entities").getString(0));
        response.put("entities", entities);
        promise.complete(response);
      } else {
        LOGGER.error(pgHandler.cause());
        JsonObject res = new JsonObject(pgHandler.cause().getMessage());
        promise.fail(generateResponse(res).toString());
      }
    });


    return promise.future();
  }

  /**
   * delete a subscription.
   * 
   * @param json subscription json
   * @param databroker databroker verticle object
   * @param databaseService database verticle object
   * @return a future of josbObject
   */
  public Future<JsonObject> deleteSubscription(JsonObject json, DataBrokerService databroker,
      PostgresService pgService) {
    LOGGER.info("deleteSubscription() method started");
    Promise<JsonObject> promise = Promise.promise();
    SubsType subType = SubsType.valueOf(json.getString(SUB_TYPE));
    subscription = getSubscriptionContext(subType, databroker, pgService);
    assertNotNull(subscription);
    subscription.delete(json).onComplete(handler -> {
      if (handler.succeeded()) {
        StringBuilder query = new StringBuilder(DELETE_SUB_SQL
            .replace("$1", json.getString(SUBSCRIPTION_ID)));

        LOGGER.debug(query);
        pgService.executeQuery(query.toString(), pgHandler -> {
          if (pgHandler.succeeded()) {
            promise.complete(handler.result());
          } else {
            JsonObject res = new JsonObject(pgHandler.cause().getMessage());
            promise.fail(generateResponse(res).toString());
          }
        });
      } else {
        JsonObject res = new JsonObject(handler.cause().getMessage());
        promise.fail(generateResponse(res).toString());
      }
    });
    return promise.future();
  }

  /**
   * get a subscription.
   * 
   * @param json subscription json
   * @param databroker databroker verticle object
   * @param databaseService database verticle object
   * @return a future of josbObject
   */
  public Future<JsonObject> getSubscription(JsonObject json, DataBrokerService databroker,
      PostgresService pgService) {
    LOGGER.info("getSubscription() method started");
    Promise<JsonObject> promise = Promise.promise();
    SubsType subType = SubsType.valueOf(json.getString(SUB_TYPE));
    subscription = getSubscriptionContext(subType, databroker, pgService);
    assertNotNull(subscription);
    subscription.get(json).onComplete(handler -> {
      if (handler.succeeded()) {
        promise.complete(handler.result());
      } else {
        JsonObject res = new JsonObject(handler.cause().getMessage());
        promise.fail(generateResponse(res).toString());
      }
    });
    return promise.future();
  }

  /**
   * append an existing subscription.
   * 
   * @param json subscription json
   * @param databroker databroker verticle object
   * @param databaseService database verticle object
   * @return a future of josbObject
   */
  public Future<JsonObject> appendSubscription(JsonObject json, DataBrokerService databroker,
      PostgresService pgService, JsonObject authInfo) {
    LOGGER.info("appendSubscription() method started");
    Promise<JsonObject> promise = Promise.promise();
    SubsType subType = SubsType.valueOf(json.getString(SUB_TYPE));
    subscription = getSubscriptionContext(subType, databroker, pgService);
    assertNotNull(subscription);
    subscription.append(json).onComplete(handler -> {
      if (handler.succeeded()) {
        promise.complete(handler.result());
        JsonObject brokerSubResult = handler.result();
        StringBuilder query = new StringBuilder(APPEND_SUB_SQL
            .replace("$1", json.getString(SUBSCRIPTION_ID))
            .replace("$2", subType.type)
            .replace("$3", json.getString(SUBSCRIPTION_ID))
            .replace("$4", json.getJsonArray("entities").getString(0))
            .replace("$5", authInfo.getString("expiry")));

        LOGGER.debug(query);
        pgService.executeQuery(query.toString(), pgHandler -> {
          if (pgHandler.succeeded()) {
            promise.complete(brokerSubResult);
          } else {
            // TODO : rollback mechanism in case of pg error [to unbind/delete created sub]
            JsonObject res = new JsonObject(pgHandler.cause().getMessage());
            promise.fail(generateResponse(res).toString());
          }
        });
      } else {
        JsonObject res = new JsonObject(handler.cause().getMessage());
        promise.fail(generateResponse(res).toString());
      }
    });
    return promise.future();
  }

  private JsonObject generateResponse(JsonObject response) {
    JsonObject finalResponse = new JsonObject();
    int type = response.getInteger(JSON_TYPE);
    String title = response.getString(JSON_TITLE);
    switch (type) {
      case 400: {
        finalResponse.put(JSON_TYPE, type)
            .put(JSON_TITLE, ResponseType.fromCode(type).getMessage())
            .put(JSON_DETAIL, response.getString(JSON_DETAIL));
        break;
      }
      case 404: {
        finalResponse.put(JSON_TYPE, type)
            .put(JSON_TITLE, ResponseType.fromCode(type).getMessage())
            .put(JSON_DETAIL, ResponseType.ResourceNotFound.getMessage());
        break;
      }
      case 409: {
        finalResponse.put(JSON_TYPE, type)
            .put(JSON_TITLE, ResponseType.fromCode(type).getMessage())
            .put(JSON_DETAIL, "Subscription " + ResponseType.AlreadyExists.getMessage());
        break;
      }
      default: {
        finalResponse.put(JSON_TYPE, type)
            .put(JSON_TITLE, ResponseType.BadRequestData.getMessage())
            .put(JSON_DETAIL, response.getString(JSON_DETAIL));
        break;
      }
    }
    return finalResponse;
  }
}
