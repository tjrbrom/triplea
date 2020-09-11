package games.strategy.triplea.xml;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.TestAttachment;
import games.strategy.engine.data.gameparser.GameParser;
import games.strategy.engine.data.gameparser.XmlGameElementMapper;
import games.strategy.triplea.delegate.TestDelegate;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;

/** The available maps for use during testing. */
public enum TestMapGameData {
  BIG_WORLD_1942("big_world_1942_test.xml"),

  BIG_WORLD_1942_V3("Big_World_1942_v3rules.xml"),

  IRON_BLITZ("iron_blitz_test.xml"),

  LHTR("lhtr_test.xml"),

  PACIFIC_INCOMPLETE("pacific_incomplete_test.xml"),

  PACT_OF_STEEL_2("pact_of_steel_2_test.xml"),

  REVISED("revised_test.xml"),

  VICTORY_TEST("victory_test.xml"),

  WW2V3_1941("ww2v3_1941_test.xml"),

  WW2V3_1942("ww2v3_1942_test.xml"),

  GLOBAL1940("ww2_g40_balanced.xml"),

  TEST("Test.xml"),

  DELEGATE_TEST("DelegateTest.xml"),

  GAME_EXAMPLE("GameExample.xml"),

  TWW("Total_World_War_Dec1941.xml"),

  MINIMAP("minimap.xml"),

  NAPOLEONIC_EMPIRES("Napoleonic_Empires.xml"),
  ;

  private final String fileName;

  TestMapGameData(final String value) {
    this.fileName = value;
  }

  @Override
  public String toString() {
    return fileName;
  }

  /**
   * Gets the game data for the associated map.
   *
   * @return The game data for the associated map.
   * @throws RuntimeException If an error occurs while loading the map.
   */
  public GameData getGameData() {
    final URI mapUri = Paths.get("src", "test", "resources", fileName).toUri();

    return GameParser.parse(
            mapUri,
            new XmlGameElementMapper(
                Map.of("TestDelegate", TestDelegate::new),
                Map.of("TestAttachment", TestAttachment::new)))
        .orElseThrow(() -> new IllegalStateException("Error parsing: " + mapUri));
  }
}
