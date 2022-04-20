package games.strategy.engine.auto.update;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.framework.map.download.DownloadMapsWindow;
import games.strategy.engine.framework.map.file.system.loader.InstalledMapsListing;
import games.strategy.engine.framework.map.listing.MapListingFetcher;
import games.strategy.triplea.settings.ClientSetting;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.triplea.http.client.maps.listing.MapDownloadItem;
import org.triplea.swing.SwingComponents;

@UtilityClass
class UpdatedMapsCheck {

  static final int THRESHOLD_DAYS = 7;

  static boolean isMapUpdateCheckRequired() {
    return isMapUpdateCheckRequired(
        ClientSetting.lastCheckForMapUpdates.getValue().orElse(0L),
        () -> ClientSetting.lastCheckForMapUpdates.setValueAndFlush(Instant.now().toEpochMilli()));
  }

  @VisibleForTesting
  static boolean isMapUpdateCheckRequired(
      final long lastCheckEpochMilli, final Runnable lastCheckSetter) {
    final Instant cutOff = Instant.now().minus(THRESHOLD_DAYS, ChronoUnit.DAYS);
    final Instant lastCheck = Instant.ofEpochMilli(lastCheckEpochMilli);

    lastCheckSetter.run();

    return lastCheck.isBefore(cutOff);
  }

  /** Prompts user to download map updates if maps are out of date. */
  public static void checkDownloadedMapsAreLatest() {
    if (!isMapUpdateCheckRequired()) {
      return;
    }

    final List<MapDownloadItem> availableToDownloadMaps = MapListingFetcher.getMapDownloadList();

    if (availableToDownloadMaps.isEmpty()) {
      // A failure happened getting maps. User is already notified.
      return;
    }

    final Collection<String> outOfDateMapNames =
        InstalledMapsListing.parseMapFiles()
            .findOutOfDateMaps(availableToDownloadMaps)
            .keySet()
            .stream()
            .map(MapDownloadItem::getMapName)
            .sorted(Comparator.comparing(String::toUpperCase))
            .collect(Collectors.toList());

    if (!outOfDateMapNames.isEmpty()) {
      promptUserToUpdateMaps(outOfDateMapNames);
    }
  }

  private static void promptUserToUpdateMaps(final Collection<String> outOfDateMapNames) {
    final StringBuilder text = new StringBuilder();
    text.append(
        "<html>The following maps have updates available."
            + "<br>Would you like to update them now?<br><ul>");
    for (final String mapName : outOfDateMapNames) {
      text.append("<li> ").append(mapName).append("</li>");
    }
    text.append("</ul></html>");
    SwingComponents.promptUser(
        "Update Your Maps?",
        text.toString(),
        () -> DownloadMapsWindow.showDownloadMapsWindowAndDownload(outOfDateMapNames));
  }
}
