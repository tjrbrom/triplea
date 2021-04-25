package games.strategy.engine.framework.map.file.system.loader;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItems;

import java.io.File;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.triplea.map.description.file.MapDescriptionYaml;

class DownloadedMapsListingTest {

  final DownloadedMapsListing downloadedMapsListing =
      new DownloadedMapsListing(
          List.of(
              new DownloadedMap(
                  MapDescriptionYaml.builder()
                      .yamlFileLocation(new File("/path/map0/map.yml").toURI())
                      .mapName("map-name0")
                      .mapVersion(0)
                      .mapGameList(
                          List.of(
                              MapDescriptionYaml.MapGame.builder()
                                  .gameName("aGame0")
                                  .xmlFileName("path.xml")
                                  .build()))
                      .build()),
              new DownloadedMap(
                  MapDescriptionYaml.builder()
                      .yamlFileLocation(new File("/path/map1/map.yml").toURI())
                      .mapName("map-name1")
                      .mapVersion(2)
                      .mapGameList(
                          List.of(
                              MapDescriptionYaml.MapGame.builder()
                                  .gameName("gameName0")
                                  .xmlFileName("path0.xml")
                                  .build(),
                              MapDescriptionYaml.MapGame.builder()
                                  .gameName("gameName1")
                                  .xmlFileName("path1.xml")
                                  .build()))
                      .build())));

  @Test
  void getSortedGamesList() {
    final List<String> sortedGameNames = downloadedMapsListing.getSortedGameList();

    assertThat(sortedGameNames, hasSize(3));
    assertThat(sortedGameNames, hasItems("aGame0", "gameName0", "gameName1"));
  }

  @Test
  void getMapVersionByName() {
    assertThat(
        "If a map does not exist, we default version value to '0'",
        downloadedMapsListing.getMapVersionByName("DNE"),
        is(0));
    assertThat(
        "This map in the listing has a version value of '0'",
        downloadedMapsListing.getMapVersionByName("map-name0"),
        is(0));
    assertThat(
        "This map in the listing has a version value of '2'",
        downloadedMapsListing.getMapVersionByName("map-name1"),
        is(2));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "DNE",
        "map_name", // does not quite match, missing trailing 0
        "map-name", // does not quite match, missing trailing 0
        "map",
        "name0"
      })
  void isMapInstalled_NegativeCases(final String mapName) {
    assertThat(downloadedMapsListing.isMapInstalled(mapName), is(false));
  }

  /** Verify match is not case sensitive with insignificant characters ignored */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "MAP NAME0",
        "MAP_NAME0",
        "MAP-NAME0",
        "map name0",
        "map_name0",
        "map-name0",
        "mapname0",
        "MAPNAME0"
      })
  void isMapInstalled_PositiveCases(final String mapName) {
    assertThat(downloadedMapsListing.isMapInstalled(mapName), is(true));
  }
}
