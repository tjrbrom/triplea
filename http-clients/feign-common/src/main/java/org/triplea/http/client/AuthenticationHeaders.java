package org.triplea.http.client;

import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.triplea.domain.data.ApiKey;
import org.triplea.domain.data.SystemIdLoader;

/** Small class to encapsulate api key and create http Authorization header. */
@AllArgsConstructor
public class AuthenticationHeaders {
  public static final String API_KEY_HEADER = "Authorization";
  public static final String KEY_BEARER_PREFIX = "Bearer";
  public static final String SYSTEM_ID_HEADER = "System-Id-Header";

  private final ApiKey apiKey;

  /** Creates headers containing both an API-Key and a 'System-Id'. */
  public Map<String, Object> createHeaders() {
    final Map<String, Object> headerMap = new HashMap<>();
    headerMap.put(API_KEY_HEADER, KEY_BEARER_PREFIX + " " + apiKey);
    headerMap.putAll(systemIdHeaders());
    return headerMap;
  }

  /** Creates headers containing 'System-Id' only. */
  public static Map<String, Object> systemIdHeaders() {
    return Map.of(SYSTEM_ID_HEADER, SystemIdLoader.load().getValue());
  }
}
