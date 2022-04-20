package games.strategy.engine.lobby.moderator.toolbox.tabs.maps;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Setter;
import org.triplea.http.client.GenericServerResponse;
import org.triplea.http.client.maps.listing.MapDownloadItem;
import org.triplea.http.client.maps.listing.MapsClient;
import org.triplea.http.client.maps.tag.admin.MapTagAdminClient;
import org.triplea.http.client.maps.tag.admin.MapTagMetaData;
import org.triplea.http.client.maps.tag.admin.UpdateMapTagRequest;

public class MapsTabModel {
  @Nonnull private final MapsClient mapsClient;
  @Nonnull private final MapTagAdminClient mapTagAdminClient;

  @Setter private MapsTabUi mapsTabUi;

  @Builder
  public MapsTabModel(
      @Nonnull final MapsClient mapsClient, @Nonnull final MapTagAdminClient mapTagAdminClient) {
    this.mapsClient = mapsClient;
    this.mapTagAdminClient = mapTagAdminClient;
  }

  /** Queries server for the list of known maps. */
  List<MapDownloadItem> fetchMapsList() {
    return mapsClient.fetchMapDownloads();
  }

  /** Queries server for the set of available tag names mapped to their allowed values. */
  List<MapTagMetaData> fetchAllowedMapTagValues() {
    return mapTagAdminClient.fetchTagsMetaData().stream()
        .sorted(Comparator.comparing(MapTagMetaData::getDisplayOrder))
        .collect(Collectors.toList());
  }

  /** Sends a request to server to update a map tag to a new value. */
  void updateMapTag(
      final String mapName, final MapTagMetaData mapTagMetaData, final String tagValue) {
    try {
      final GenericServerResponse serverResponse =
          mapTagAdminClient.updateMapTag(
              UpdateMapTagRequest.builder()
                  .mapName(mapName)
                  .tagName(mapTagMetaData.getTagName())
                  .newTagValue(tagValue)
                  .build());
      mapsTabUi.showMessage(
          String.format(
              "%s: %s",
              serverResponse.isSuccess() ? "Success" : "Error", serverResponse.getMessage()));
    } catch (final Exception e) {
      mapsTabUi.showMessage(String.format("Error: %s", e));
    }
  }
}
