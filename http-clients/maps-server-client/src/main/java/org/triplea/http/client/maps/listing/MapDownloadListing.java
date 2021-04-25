package org.triplea.http.client.maps.listing;

import javax.annotation.Nonnull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Builder
@AllArgsConstructor
@Getter
public class MapDownloadListing {
  @Nonnull private final String url;
  @Nonnull private final String mapName;
  @Nonnull private final String version;
  @Nonnull private final String mapCategory;
}
