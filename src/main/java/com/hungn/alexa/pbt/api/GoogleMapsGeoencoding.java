package com.hungn.alexa.pbt.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * Helper class to query Google Maps Geocoding API to reverse geocoding to an address
 *
 * @author Hung Nguyen (hungng@seas.upenn.edu)
 */
public class GoogleMapsGeoencoding {
  public static final String BASE_SCHEME = "https";
  public static final String BASE_HOST = "maps.googleapis.com";
  public static final String BASE_URL = "maps/api/geocode/json";
  private static final String API_KEY = "AIzaSyA4h6XL16EJrEjdykvBtcmrG2ZyImyz7p0";

  private static final Log log = LogFactory.getLog(GoogleMapsGeoencoding.class);

  private static final String AGENT = "PennBusTracker-AlexaSkill";
  private static ObjectMapper mapper;
  private RequestConfig requestConfig;

  public GoogleMapsGeoencoding() {
    requestConfig = RequestConfig.custom()
        .setConnectionRequestTimeout(5000)
        .setConnectTimeout(2000)
        .setSocketTimeout(5000)
        .build();
  }

  public String getStreetAddress(double longitude, double latitude) {
    if (mapper == null) mapper = new ObjectMapper();

    // Query PennRides API
    try (CloseableHttpClient client = HttpClients.createDefault()) {
      URIBuilder uri = new URIBuilder();
      uri.setScheme(BASE_SCHEME);
      uri.setHost(BASE_HOST);
      uri.setPath(BASE_URL);
      uri.setParameter("latlng", Double.toString(latitude) + "," + Double.toString(longitude));
      uri.setParameter("key", API_KEY);
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
        if (root.path("status").isMissingNode() ||
            !root.path("status").asText().equalsIgnoreCase("ok")) {
          // -- first verify the status
          if (log.isInfoEnabled()) {
            log.info("Failed to query Google Maps API -- status: " +
                (root.path("status").isMissingNode() ? "missing" : root.path("status").asText()));
          }
          return null;
        } else {
          // -- check if results are returned
          if (root.path("results").isArray() && root.path("results").size() > 0) {
            for (JsonNode node : root.path("results")) {
              if (!node.path("formatted_address").isMissingNode()) {
                return node.path("formatted_address").asText().split(",")[0];
              }
            }
          }
          return null;
        }
      } else {
        if (log.isInfoEnabled()) {
          log.info("Failed to query Google Maps API -- code "
              + response.getStatusLine().getStatusCode());
        }
        return null;
      }
    } catch (Exception ex) {
      log.error("Failed to query Google Maps API!", ex);
      return null;
    }
  }
}
