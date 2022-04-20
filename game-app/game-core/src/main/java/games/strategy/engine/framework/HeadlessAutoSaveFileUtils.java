package games.strategy.engine.framework;

import static games.strategy.engine.framework.CliProperties.TRIPLEA_NAME;
import static games.strategy.engine.framework.GameDataFileUtils.addExtension;

import java.nio.file.Path;

/** Headless variant of {@link AutoSaveFileUtils} with slightly shortened save-game names. */
public class HeadlessAutoSaveFileUtils extends AutoSaveFileUtils {
  @Override
  String getAutoSaveFileName(final String baseFileName) {
    final String prefix = System.getProperty(TRIPLEA_NAME, "");
    if (!prefix.isEmpty()) {
      return prefix + "_" + baseFileName;
    }
    return super.getAutoSaveFileName(baseFileName);
  }

  @Override
  public String getAutoSaveStepName(final String stepName) {
    return stepName.endsWith("NonCombatMove") ? "NonCombatMove" : "CombatMove";
  }

  public Path getHeadlessAutoSaveFile() {
    return getAutoSaveFile(addExtension("autosave"));
  }
}
