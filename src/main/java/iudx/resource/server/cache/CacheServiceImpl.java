package iudx.resource.server.cache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.resource.server.cache.cacheImpl.CacheType;
import iudx.resource.server.cache.cacheImpl.IudxCache;
import iudx.resource.server.cache.cacheImpl.RevokedClientCache;
import iudx.resource.server.cache.cacheImpl.UniqueAttributeCache;
import iudx.resource.server.database.postgres.PostgresService;

public class CacheServiceImpl implements CacheService {

  private static final Logger LOGGER = LogManager.getLogger(CacheServiceImpl.class);

  private IudxCache revokedClientCache;
  private IudxCache uniqueAttributeCache;
  private PostgresService postgresService;

  public CacheServiceImpl(Vertx vertx, PostgresService pgService) {
    this.postgresService = pgService;
    revokedClientCache = new RevokedClientCache(vertx, postgresService);
    uniqueAttributeCache = new UniqueAttributeCache(vertx, postgresService);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CacheService get(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    IudxCache cache = null;

    try {
      cache = getCache(request);
    } catch (IllegalArgumentException ex) {
      LOGGER.error("No cache defined for given argument.");
      handler.handle(Future.failedFuture("No cache defined for given type"));
      return this;
    }

    String key = request.getString("key");

    if (key != null) {
      String value = cache.get(key);
      if (value != null) {
        JsonObject json = new JsonObject();
        json.put("value", value);
        handler.handle(Future.succeededFuture(json));
      } else {
        handler.handle(Future.failedFuture("No entry for given key"));
      }
    } else {
      handler.handle(Future.failedFuture("null key passed."));
    }


    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CacheService put(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.trace("message received from for cache put operation");
    LOGGER.debug("message : " + request);

    IudxCache cache = null;
    try {
      cache = getCache(request);
    } catch (IllegalArgumentException ex) {
      LOGGER.error("No cache defined for given argument.");
      handler.handle(Future.failedFuture("No cache defined for given type"));
      return this;
    }

    String key = request.getString("key");
    String value = request.getString("value");
    if (key != null && value != null) {
      cache.put(key, value);
      handler.handle(Future.succeededFuture(new JsonObject().put(key, value)));
    } else {
      handler.handle(Future.failedFuture("'null' key or value not allowed in cache."));
    }
    return this;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public CacheService refresh(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.trace("message received for cache refresh()");
    LOGGER.debug("message : " + request);
    IudxCache cache = null;
    try {
      cache = getCache(request);
    } catch (IllegalArgumentException ex) {
      LOGGER.error("No cache defined for given argument.");
      handler.handle(Future.failedFuture("No cache defined for given type"));
      return this;
    }
    String key = request.getString("key");
    String value = request.getString("value");

    if (key != null && value != null) {
      cache.put(key, value);
    } else {
      cache.refreshCache();
    }
    handler.handle(Future.succeededFuture());
    return this;
  }

  private IudxCache getCache(JsonObject json) {
    if (!json.containsKey("type")) {
      throw new IllegalArgumentException("No cache type specified");
    }

    CacheType cacheType = CacheType.valueOf(json.getString("type"));
    IudxCache cache = null;
    switch (cacheType) {
      case REVOKED_CLIENT: {
        cache = revokedClientCache;
        break;
      }
      case UNIQUE_ATTRIBUTE: {
        cache = uniqueAttributeCache;
        break;
      }
      default: {
        throw new IllegalArgumentException("No cache type specified");
      }
    }
    return cache;
  }

}
