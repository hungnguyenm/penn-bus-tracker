package com.hungn.alexa.pbt.api;

import com.hungn.alexa.pbt.types.VehicleStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * Holder for PennRides information such as base URLs, route, way points.
 *
 * @author Hung Nguyen (hungng@seas.upenn.edu)
 */
public class PennRides {
  public static final String BASE_SCHEME = "https";
  public static final String BASE_HOST = "pennrides.com";
  public static final String BASE_URL = "Route/";
  public static final String PATH_DIRECTIONS = "/directions/";
  public static final String PATH_STOPS = "/stops/";
  public static final String PATH_VEHICLES = "/vehicles/";
  public static final String PATH_WAYPOINTS = "/waypoints/";

  private static final Logger log = LoggerFactory.getLogger(PennRides.class);

  private static final DateTimeFormatter dtGenerator =
      DateTimeFormatter.ofPattern("z dd-MM-yyyy ").withZone(ZoneId.of("America/New_York"));
  private static final DateTimeFormatter dtParser = DateTimeFormatter.ofPattern("z dd-MM-yyyy h:mm:ssa");
  private static final String AGENT = "PennBusTracker-AlexaSkill";
  private static ObjectMapper mapper;
  private RequestConfig requestConfig;

  /**
   * Enumerate class to hold different bus routes (which are used to encoded
   * in PennRides backend)
   */
  public enum Route {
    SHUTTLE_EAST(3898), SHUTTLE_WEST_A(3899), SHUTTLE_WEST_B(3900),
    BUS_EAST(229), BUS_WEST(230),
    PENNOVATION(4613);

    private final int route;

    Route(int route) {
      this.route = route;
    }

    public int getRoute() {
      return route;
    }
  }

  public PennRides() {
    requestConfig = RequestConfig.custom()
        .setConnectionRequestTimeout(5000)
        .setConnectTimeout(2000)
        .setSocketTimeout(5000)
        .build();
  }

  /**
   * Query the latest vehicles' status on a specific route
   *
   * @param route the specific route to be queried
   * @return list of vehicles' status; or an empty list if no bus is operating on this route;
   * or null if error happens
   */
  public LinkedList<VehicleStatus> getVehicleStatus(Route route) {
    if (mapper == null) mapper = new ObjectMapper();
    LinkedList<VehicleStatus> vehicles = new LinkedList<>();
    ZonedDateTime now = ZonedDateTime.now();

    // Query PennRides API
    try (CloseableHttpClient client = HttpClients.createDefault()) {
      URIBuilder uri = new URIBuilder();
      uri.setScheme(BASE_SCHEME);
      uri.setHost(BASE_HOST);
      uri.setPath(BASE_URL + Integer.toString(route.getRoute()) + PATH_VEHICLES);
      HttpGet httpget = new HttpGet();
      httpget.setURI(uri.build());
      httpget.setConfig(requestConfig);
      httpget.setHeader("User-Agent", AGENT);

      HttpResponse response = client.execute(httpget);
      ResponseHandler<String> handler = new BasicResponseHandler();
      if (response.getStatusLine().getStatusCode() == 200) {
        // Parse results
        String content = handler.handleResponse(response);
        JsonNode root = mapper.readTree(content);
        if (root.isArray() && root.size() > 0) {
          // There are track-able vehicles
          for (JsonNode node : root) {
            if (!node.path("ID").isMissingNode() &&
                !node.path("RouteId").isMissingNode() &&
                !node.path("PatternId").isMissingNode() &&
                !node.path("Name").isMissingNode() &&
                !node.path("Longitude").isMissingNode() &&
                !node.path("Latitude").isMissingNode() &&
                !node.path("Heading").isMissingNode() &&
                !node.path("Updated").isMissingNode()) {
              VehicleStatus status = new VehicleStatus();
              status.setId(node.path("ID").asInt());
              status.setRouteId(node.path("RouteId").asInt());
              status.setPatternId(node.path("PatternId").asInt());
              status.setName(node.path("Name").asText());
              status.setLongitude(node.path("Longitude").asDouble());
              status.setLatitude(node.path("Latitude").asDouble());
              status.setHeading(getHeading(node.path("Heading").asText()));

              ZonedDateTime updatedTime = ZonedDateTime.parse(now.format(dtGenerator)
                  + node.path("Updated").asText() + "M", dtParser);
              if (updatedTime.isAfter(now)) updatedTime.minusDays(1);
              status.setLastUpdated(getElapsedTime(updatedTime.toEpochSecond(), now.toEpochSecond()));

              vehicles.add(status);
            }
          }
        }
      } else {
        if (log.isInfoEnabled()) {
          log.info("Failed to query PennRides -- code "
              + response.getStatusLine().getStatusCode());
        }
        return null;
      }
    } catch (Exception ex) {
      log.error("Failed to query PennRides!", ex);
      return null;
    }

    return vehicles;
  }

  /**
   * Get heading direction from encoded characters
   *
   * @param encoded encoded direction such as NE, E, W, NW
   * @return decoded direction
   */
  private String getHeading(String encoded) {
    switch (encoded) {
      case "E":
        return "East";
      case "NE":
        return "North East";
      case "SE":
        return "South East";
      case "W":
        return "West";
      case "NW":
        return "North West";
      case "SW":
        return "South West";
      case "N":
        return "North";
      case "S":
        return "South";
      default:
        return "";
    }
  }

  /**
   * Helper function to prettify elapsed time (alexa readable string)
   *
   * @param start start time
   * @param end end time
   * @return readable elapsed time as a string
   */
  public static String getElapsedTime(long start, long end) {
    String ret = "";
    long diff = end - start;

    if(diff < 0) return "unknown";

    long days = TimeUnit.SECONDS.toDays(diff);
    diff -= TimeUnit.DAYS.toMillis(days);
    long hours = TimeUnit.SECONDS.toHours(diff);
    diff -= TimeUnit.HOURS.toMillis(hours);
    long minutes = TimeUnit.SECONDS.toMinutes(diff);
    diff -= TimeUnit.MINUTES.toMillis(minutes);
    long seconds = TimeUnit.SECONDS.toSeconds(diff);

    if (days > 0) {
      ret += days + " days ";
      ret += hours + " hours";
    } else if (hours > 0) {
      ret += hours + " hours ";
      ret += minutes + " minutes";
    } else if (minutes > 0) {
      ret += minutes + " minutes ";
      ret += seconds + " seconds";
    } else {
      ret += seconds + " seconds";
    }

    return ret + " ago";
  }
}
