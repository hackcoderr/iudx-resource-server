package iudx.resource.server.apiserver.query;

import static iudx.resource.server.common.HttpStatusCode.BAD_REQUEST;
import static iudx.resource.server.common.ResponseUrn.INVALID_ATTR_PARAM_URN;
import static iudx.resource.server.common.ResponseUrn.INVALID_GEO_PARAM_URN;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.resource.server.apiserver.exceptions.DxRuntimeException;
import iudx.resource.server.apiserver.util.Constants;
import iudx.resource.server.common.ResponseUrn;

/**
 * QueryMapper class to convert NGSILD query into json object for the purpose of information
 * exchange among different verticals. TODO Need to add documentation.
 */
public class QueryMapper {

  private static final Logger LOGGER = LogManager.getLogger(QueryMapper.class);
  private boolean isTemporal = false;
  private boolean isGeoSearch = false;
  private boolean isResponseFilter = false;
  private boolean isAttributeSearch = false;

  /**
   * This method is used to create a json object from NGSILDQueryParams.
   * 
   * @param params A map of query parameters passed.
   * @param isTemporal flag indicating whether temporal or not.
   * @return JsonObject result.
   */
  public JsonObject toJson(NGSILDQueryParams params, boolean isTemporal) {
    LOGGER.trace("Info QueryMapper#toJson() started");
    LOGGER.debug("Info : params" + params);
    this.isTemporal = isTemporal;
    JsonObject json = new JsonObject();

    if (params.getId() != null) {
      JsonArray jsonArray = new JsonArray();
      params.getId().forEach(s -> jsonArray.add(s.toString()));
      json.put(Constants.JSON_ID, jsonArray);
      LOGGER.debug("Info : json " + json);
    }
    if (params.getAttrs() != null) {
      isResponseFilter = true;
      JsonArray jsonArray = new JsonArray();
      params.getAttrs().forEach(attribute -> jsonArray.add(attribute));
      json.put(Constants.JSON_ATTRIBUTE_FILTER, jsonArray);
      LOGGER.debug("Info : json " + json);
    }
    if (isGeoQuery(params)) {
      if (params.getGeoRel().getRelation() != null && params.getCoordinates() != null
          && params.getGeometry() != null && params.getGeoProperty() != null) {
        isGeoSearch = true;
        if (params.getGeometry().equalsIgnoreCase(Constants.GEOM_POINT)
            && params.getGeoRel().getRelation().equals(Constants.JSON_NEAR)
            && params.getGeoRel().getMaxDistance() != null) {
          String[] coords = params.getCoordinates().replaceAll("\\[|\\]", "").split(",");
          json.put(Constants.JSON_LAT, Double.parseDouble(coords[0]));
          json.put(Constants.JSON_LON, Double.parseDouble(coords[1]));
          json.put(Constants.JSON_RADIUS, params.getGeoRel().getMaxDistance());
        } else {
          json.put(Constants.JSON_GEOMETRY, params.getGeometry());
          json.put(Constants.JSON_COORDINATES, params.getCoordinates());
          json.put(Constants.JSON_GEOREL,
              getOrDefault(params.getGeoRel().getRelation(), Constants.JSON_WITHIN));
          if (params.getGeoRel().getMaxDistance() != null) {
            json.put(Constants.JSON_MAXDISTANCE, params.getGeoRel().getMaxDistance());
          } else if (params.getGeoRel().getMinDistance() != null) {
            json.put(Constants.JSON_MINDISTANCE, params.getGeoRel().getMinDistance());
          }
        }
        LOGGER.debug("Info : json " + json);
      } else {
        throw new DxRuntimeException(BAD_REQUEST.getValue(), INVALID_GEO_PARAM_URN,
            "incomplete geo-query geoproperty, geometry, georel, coordinates all are mandatory.");
      }
    }
    if (isTemporal && params.getTemporalRelation().getTemprel() != null
        && params.getTemporalRelation().getTime() != null) {
      isTemporal = true;
      if (params.getTemporalRelation().getTemprel().equalsIgnoreCase(Constants.JSON_DURING)) {
        LOGGER.debug("Info : inside during ");

        json.put(Constants.JSON_TIME, params.getTemporalRelation().getTime());
        json.put(Constants.JSON_ENDTIME, params.getTemporalRelation().getEndTime());
        json.put(Constants.JSON_TIMEREL, params.getTemporalRelation().getTemprel());
        isValidTimeInterval(Constants.JSON_DURING, json.getString(Constants.JSON_TIME),
            json.getString(Constants.JSON_ENDTIME));
      } else {
        json.put(Constants.JSON_TIME, params.getTemporalRelation().getTime().toString());
        json.put(Constants.JSON_TIMEREL, params.getTemporalRelation().getTemprel());
      }
      LOGGER.debug("Info : json " + json);
    }
    if (params.getQ() != null) {
      isAttributeSearch = true;
      JsonArray query = new JsonArray();
      String[] qterms = params.getQ().split(";");
      for (String term : qterms) {
        query.add(getQueryTerms(term));
      }
      json.put(Constants.JSON_ATTR_QUERY, query);
      LOGGER.debug("Info : json " + json);
    }
    if (params.getGeoProperty() != null) {
      json.put(Constants.JSON_GEOPROPERTY, params.getGeoProperty());
      LOGGER.debug("Info : json " + json);
    }
    if (params.getOptions() != null) {
      json.put(Constants.IUDXQUERY_OPTIONS, params.getOptions());
      LOGGER.debug("Info : json " + json);
    }
    if (params.getPageFrom() != null) {
      json.put(Constants.NGSILDQUERY_FROM, params.getPageFrom());
    }
    if (params.getPageSize() != null) {
      json.put(Constants.NGSILDQUERY_SIZE, params.getPageSize());
    }

    json.put(Constants.JSON_SEARCH_TYPE, getSearchType());
    LOGGER.debug("Info : json " + json);
    return json;
  }

  /*
   * check for a valid days interval for temporal queries
   */
  // TODO : decide how to enforce for before and after queries.
  private void isValidTimeInterval(String timeRel, String time, String endTime) {
    long totalDaysAllowed = 0;
    if (timeRel.equalsIgnoreCase(Constants.JSON_DURING)) {
      if (isNullorEmpty(time) || isNullorEmpty(endTime)) {
        throw new DxRuntimeException(BAD_REQUEST.getValue(), ResponseUrn.INVALID_TEMPORAL_PARAM_URN,
            "time and endTime both are mandatory for during Query.");
      }

      try {
        ZonedDateTime start = ZonedDateTime.parse(time);
        ZonedDateTime end = ZonedDateTime.parse(endTime);
        Duration duration = Duration.between(start, end);
        totalDaysAllowed = duration.toDays();
      } catch (Exception ex) {
        throw new DxRuntimeException(BAD_REQUEST.getValue(), ResponseUrn.INVALID_TEMPORAL_PARAM_URN,
            "time and endTime both are mandatory for during Query.");

      }
    } else if (timeRel.equalsIgnoreCase("after")) {
      // how to enforce days duration for after and before,i.e here or DB
    } else if (timeRel.equalsIgnoreCase("before")) {

    }
    if (totalDaysAllowed > Constants.VALIDATION_MAX_DAYS_INTERVAL_ALLOWED) {
      throw new DxRuntimeException(BAD_REQUEST.getValue(), ResponseUrn.INVALID_TEMPORAL_PARAM_URN,
          "time interval greater than 10 days is not allowed");
    }
  }

  private boolean isGeoQuery(NGSILDQueryParams params) {
    LOGGER
        .debug("georel " + params.getGeoRel() + " relation : " + params.getGeoRel().getRelation());
    return params.getGeoRel().getRelation() != null || params.getCoordinates() != null
        || params.getGeometry() != null || params.getGeoProperty() != null;
  }

  private boolean isNullorEmpty(String value) {
    if (value != null && !value.isEmpty())
      return false;
    return true;
  }


  private <T> T getOrDefault(T value, T def) {
    return (value == null) ? def : value;
  }

  private String getSearchType() {
    StringBuilder searchType = new StringBuilder();
    if (isTemporal) {
      searchType.append(Constants.JSON_TEMPORAL_SEARCH);
    } else {
      searchType.append(Constants.JSON_LATEST_SEARCH);
    }
    if (isGeoSearch) {
      searchType.append(Constants.JSON_GEO_SEARCH);
    }
    if (isResponseFilter) {
      searchType.append(Constants.JSON_RESPONSE_FILTER_SEARCH);
    }
    if (isAttributeSearch) {
      searchType.append(Constants.JSON_ATTRIBUTE_SEARCH);
    }
    return searchType.substring(0, searchType.length() - 1).toString();
  }

  JsonObject getQueryTerms(String queryTerms) {
    JsonObject json = new JsonObject();
    int length = queryTerms.length();
    List<Character> allowedSpecialCharacter = Arrays.asList('>', '=', '<', '!');
    List<String> allowedOperators = Arrays.asList(">", "=", "<", ">=", "<=", "==", "!=");
    int startIndex = 0;
    boolean specialCharFound = false;
    for (int i = 0; i < length; i++) {
      Character c = queryTerms.charAt(i);
      if (!(Character.isLetter(c) || Character.isDigit(c)) && !specialCharFound) {
        if (allowedSpecialCharacter.contains(c)) {
          json.put(Constants.JSON_ATTRIBUTE, queryTerms.substring(startIndex, i));
          startIndex = i;
          specialCharFound = true;
        } else {
          LOGGER.debug("Ignore " + c.toString());
          throw new DxRuntimeException(BAD_REQUEST.getValue(), INVALID_ATTR_PARAM_URN, "Operator not allowed.");
        }
      } else {
        if (specialCharFound && (Character.isLetter(c) || Character.isDigit(c))) {
          json.put(Constants.JSON_OPERATOR, queryTerms.substring(startIndex, i));
          json.put(Constants.JSON_VALUE, queryTerms.substring(i));
          break;
        }
      }

    }
    if (!allowedOperators.contains(json.getString(Constants.JSON_OPERATOR))) {
      throw new DxRuntimeException(BAD_REQUEST.getValue(), INVALID_ATTR_PARAM_URN, "Operator not allowed.");
    }
    return json;
  }

}
