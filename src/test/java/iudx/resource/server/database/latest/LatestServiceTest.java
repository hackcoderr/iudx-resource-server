package iudx.resource.server.database.latest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import iudx.resource.server.cache.CacheService;
import iudx.resource.server.configuration.Configuration;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
public class LatestServiceTest {
  private static final Logger LOGGER = LogManager.getLogger(LatestDataService.class);
  private static Vertx vertxObj;
  private static RedisClient client;
  private static Configuration config;
  private static JsonObject attributeList;

  private static RedisClient redisClient;
  private static LatestDataService latest;
  private static CacheService cacheService;

  @BeforeAll
  @DisplayName("Deploying Latest Test Verticle")
  static void init(Vertx vertx, VertxTestContext testContext) {
    vertxObj = vertx;
    config = new Configuration();
    JsonObject redisConfig = config.configLoader(5, vertx);
    attributeList = redisConfig.getJsonObject("attributeList");
    new RedisClient(vertx, redisConfig).start().onSuccess(handler -> {
      redisClient = handler;
      cacheService = Mockito.mock(CacheService.class);
      latest = new LatestDataServiceImpl(redisClient, cacheService);

      testContext.completeNow();
    }).onFailure(handler -> {
      testContext.failNow("fail to start vertx");
    });
  }


  /**
   * resource-id query resource-group aqm rg flood rg itms
   */

  @Test
  @DisplayName("Testing Latest Data at resource level- flood")
  void searchLatestResourceflood(VertxTestContext testContext) {
    String id =
        "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/pune-env-flood/FWR055";
    JsonObject request =
        new JsonObject()
            .put("id", new JsonArray().add(id))
            .put("searchType", "latestSearch");

    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    when(asyncResult.succeeded()).thenReturn(false);

    Mockito.doAnswer(new Answer<AsyncResult<JsonObject>>() {
      @SuppressWarnings("unchecked")
      @Override
      public AsyncResult<JsonObject> answer(InvocationOnMock arg0) throws Throwable {
        ((Handler<AsyncResult<JsonObject>>) arg0.getArgument(1)).handle(asyncResult);
        return null;
      }
    }).when(cacheService).get(any(), any());

    latest.getLatestData(request, handler -> {
      if (handler.succeeded()) {
        LOGGER.debug("Got the data!");
        testContext.completeNow();
      } else {
        testContext.failNow(handler.cause());
      }
    });
  }

  @Test
  @DisplayName("Testing Latest Data at resource level- flood (id not present in redis)")
  void searchLatestResourcefloodIdnotPresent(VertxTestContext testContext) {
    String id =
        "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/pune-env-flood/FWR018";
    JsonObject request =
        new JsonObject()
            .put("id", new JsonArray().add(id))
            .put("searchType", "latestSearch");

    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    when(asyncResult.succeeded()).thenReturn(false);

    Mockito.doAnswer(new Answer<AsyncResult<JsonObject>>() {
      @SuppressWarnings("unchecked")
      @Override
      public AsyncResult<JsonObject> answer(InvocationOnMock arg0) throws Throwable {
        ((Handler<AsyncResult<JsonObject>>) arg0.getArgument(1)).handle(asyncResult);
        return null;
      }
    }).when(cacheService).get(any(), any());

    latest.getLatestData(request, handler -> {
      if (handler.succeeded()) {
        testContext.failNow(handler.cause());
      } else {
        testContext.completeNow();
      }
    });
  }

  @Test
  @DisplayName("Testing Latest Data at resource level- itms")
  void searchLatestResourceItms(VertxTestContext testContext) {
    String id =
        "suratmunicipal.org/6db486cb4f720e8585ba1f45a931c63c25dbbbda/rs.iudx.org.in/surat-itms-realtime-info/surat-itms-live-eta";
    JsonObject request =
        new JsonObject()
            .put("id", new JsonArray().add(id))
            .put("searchType", "latestSearch");

    // prepare mocked response from database/postgres service.
    JsonObject cacheResponse = new JsonObject();
    cacheResponse.put("value", "license_plate");

    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(cacheResponse);

    Mockito.doAnswer(new Answer<AsyncResult<JsonObject>>() {
      @SuppressWarnings("unchecked")
      @Override
      public AsyncResult<JsonObject> answer(InvocationOnMock arg0) throws Throwable {
        ((Handler<AsyncResult<JsonObject>>) arg0.getArgument(1)).handle(asyncResult);
        return null;
      }
    }).when(cacheService).get(any(), any());

    latest.getLatestData(request, handler -> {
      if (handler.succeeded()) {
        LOGGER.debug("Got the data!");
        testContext.completeNow();
      } else {
        testContext.failNow(handler.cause());
      }
    });
  }


  @Test
  @DisplayName("Testing Basic Exceptions (No id key)")
  void searchWithNoId(VertxTestContext testContext) {
    JsonObject request = new JsonObject().put("options", "id");

    latest.getLatestData(request, testContext.failing(response -> testContext.verify(() -> {
      assertEquals("No id found", new JsonObject(response.getMessage()).getString(
          "detail"));
      testContext.completeNow();
    })));
  }

  @Test
  @DisplayName("Testing Basic Exceptions (id is empty)")
  void searchEmptyId(VertxTestContext testContext) {
    JsonObject request =
        new JsonObject().put("id", new JsonArray()).put("options", "id");

    latest.getLatestData(request, testContext.failing(response -> testContext.verify(() -> {
      assertEquals("resource-id is empty", new JsonObject(response.getMessage()).getString(
          "detail"));
      testContext.completeNow();
    })));
  }

}
