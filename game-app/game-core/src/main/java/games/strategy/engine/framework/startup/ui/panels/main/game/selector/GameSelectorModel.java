package games.strategy.engine.framework.startup.ui.panels.main.game.selector;

import com.google.common.base.Preconditions;
import games.strategy.engine.ClientFileSystemHelper;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.gameparser.GameParser;
import games.strategy.engine.data.gameparser.GameParsingValidation;
import games.strategy.engine.framework.GameDataManager;
import games.strategy.engine.framework.GameShutdownRegistry;
import games.strategy.engine.framework.startup.mc.ClientModel;
import games.strategy.engine.framework.startup.mc.GameSelector;
import games.strategy.triplea.settings.ClientSetting;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Observable;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.triplea.java.ThreadRunner;

/**
 * Model class that tracks the currently 'selected' game. This is the info that appears in the game
 * selector panel on the staging screens, eg: map, round, filename.
 */
@Slf4j
public class GameSelectorModel extends Observable implements GameSelector {

  private final Function<File, Optional<GameData>> gameParser;

  @Nullable
  @Getter(onMethod_ = {@Override})
  private GameData gameData = null;

  @Getter private String gameName = "-";
  @Getter private String gameRound = "-";
  @Nullable private String fileName;
  @Getter private boolean canSelect = true;
  @Getter private boolean hostIsHeadlessBot = false;
  // just for host bots, so we can get the actions for loading/saving games on the bots from this
  // model
  @Setter @Getter private ClientModel clientModelForHostBots = null;

  public GameSelectorModel() {
    this.gameParser = GameParser::parse;
  }

  /**
   * Loads game data by parsing a given file.
   *
   * @return True if successfully loaded, otherwise false.
   */
  public boolean load(final File xmlFile) {
    Preconditions.checkArgument(
        xmlFile.exists(),
        "Programming error, expected file to have already been checked to exist: "
            + xmlFile.getAbsolutePath());

    // if the file name is xml, load it as a new game
    if (xmlFile.getName().toLowerCase().endsWith("xml")) {
      fileName = null;
      GameData gameData = parseAndValidate(xmlFile);
      if (gameData != null && gameData.getGameName() == null) {
        gameData = null;
      }
      setGameData(gameData);
      this.setDefaultGame(xmlFile, gameData);
      return gameData != null;
    } else {
      // try to load it as a saved game whatever the extension
      final GameData newData = GameDataManager.loadGame(xmlFile).orElse(null);
      if (newData == null) {
        return false;
      }
      newData.setSaveGameFileName(xmlFile.getName());
      this.fileName = xmlFile.getName();
      setGameData(newData);
      return true;
    }
  }

  private void setDefaultGame(@Nullable final File xmlFile, @Nullable final GameData gameData) {
    if (gameData == null || xmlFile == null) {
      ClientSetting.defaultGameName.resetValue();
      ClientSetting.defaultGameUri.resetValue();
    } else {
      ClientSetting.defaultGameName.setValue(gameData.getGameName());
      ClientSetting.defaultGameUri.setValue(xmlFile.getAbsolutePath());
    }
    ClientSetting.flush();
  }

  private void resetDefaultGame() {
    setGameData(null);
    setDefaultGame(null, null);
  }

  @Nullable
  private GameData parseAndValidate(final File file) {
    final GameData gameData = gameParser.apply(file).orElse(null);
    if (gameData == null) {
      return null;
    }
    final List<String> validationErrors = new GameParsingValidation(gameData).validate();

    if (validationErrors.isEmpty()) {
      return gameData;
    } else {
      log.error(
          "Validation errors parsing game-XML file: {}, errors:\n{}",
          file.getAbsolutePath(),
          String.join("\n", validationErrors));
      return null;
    }
  }

  public void setCanSelect(final boolean canSelect) {
    this.canSelect = canSelect;
    notifyObs();
  }

  public void setIsHostHeadlessBot(final boolean isHostHeadlessBot) {
    this.hostIsHeadlessBot = isHostHeadlessBot;
    notifyObs();
  }

  /**
   * We don't have a game data (i.e. we are a remote player and the data has not been sent yet), but
   * we still want to display game info.
   */
  public void clearDataButKeepGameInfo(final String gameName, final String gameRound) {
    synchronized (this) {
      gameData = null;
      this.gameName = gameName;
      this.gameRound = gameRound;
    }
    notifyObs();
  }

  public String getFileName() {
    return Optional.ofNullable(fileName).orElse("-");
  }

  public void setGameData(final GameData data) {
    synchronized (this) {
      if (data == null) {
        gameName = gameRound = "-";
      } else {
        gameName = data.getGameName();
        gameRound = "" + data.getSequence().getRound();
      }
      gameData = data;
    }
    notifyObs();
  }

  private void notifyObs() {
    super.setChanged();
    super.notifyObservers(gameData);
    super.clearChanged();
  }

  /** Clears AI game over cache and loads default game in a new thread. */
  @Override
  public void onGameEnded() {
    // clear out ai cached properties (this ended up being the best place to put it,
    // as we have definitely left a game at this point)
    GameShutdownRegistry.runShutdownActions();
    ThreadRunner.runInNewThread(this::loadDefaultGameSameThread);
  }

  /**
   * Runs the load default game logic in same thread. Default game is the one that we loaded on
   * startup.
   */
  public void loadDefaultGameSameThread() {
    ClientSetting.defaultGameUri
        .getValue()
        .filter(Predicate.not(String::isBlank))
        .filter(GameSelectorModel::gameUriExistsOnFileSystem)
        .map(Path::of)
        .map(Path::toFile)
        .ifPresentOrElse(this::load, this::resetDefaultGame);
  }

  @SuppressWarnings("ReturnValueIgnored")
  private static boolean gameUriExistsOnFileSystem(final String gameUri) {
    try {
      final Path gameFile = Path.of(gameUri);

      // starts with check is because we don't want to load a game file by default that is not
      // within
      // the map folders. (ie: if a previous version of triplea was using running a game within its
      // root folder, we shouldn't open it)
      return Files.exists(gameFile)
          && gameFile.startsWith(ClientFileSystemHelper.getUserRootFolder());
    } catch (final IllegalArgumentException e) {
      log.info("Default game uri {} could not be loaded", gameUri, e);
      return false;
    }
  }
}
