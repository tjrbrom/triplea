package games.strategy.engine.framework.startup.ui.panels.main.game.selector;

import com.google.common.base.Preconditions;
import games.strategy.engine.ClientFileSystemHelper;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.gameparser.GameParser;
import games.strategy.engine.data.gameparser.XmlGameElementMapper;
import games.strategy.engine.framework.GameDataManager;
import games.strategy.engine.framework.startup.mc.ClientModel;
import games.strategy.engine.framework.startup.mc.GameSelector;
import games.strategy.triplea.ai.pro.ProAi;
import games.strategy.triplea.settings.ClientSetting;
import java.io.File;
import java.net.URI;
import java.util.Observable;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

/**
 * Model class that tracks the currently 'selected' game. This is the info that appears in the game
 * selector panel on the staging screens, eg: map, round, filename.
 */
@Log
public class GameSelectorModel extends Observable implements GameSelector {

  private final Function<URI, Optional<GameData>> gameParser;

  @Nullable
  @Getter(onMethod_ = {@Override})
  private GameData gameData = null;

  @Getter private String gameName = "-";
  @Getter private String gameVersion = "-";
  @Getter private String gameRound = "-";
  @Nullable private String fileName;
  @Getter private boolean canSelect = true;
  @Getter private boolean hostIsHeadlessBot = false;
  // just for host bots, so we can get the actions for loading/saving games on the bots from this
  // model
  @Setter @Getter private ClientModel clientModelForHostBots = null;

  public GameSelectorModel() {
    this(uri -> GameParser.parse(uri, new XmlGameElementMapper()));
  }

  GameSelectorModel(final Function<URI, Optional<GameData>> gameParser) {
    this.gameParser = gameParser;
  }

  public void load(final URI uri) {
    fileName = null;
    this.gameData = gameParser.apply(uri).orElse(null);
    if (gameData == null || gameData.getGameName() == null || uri == null) {
      ClientSetting.defaultGameName.resetValue();
      ClientSetting.defaultGameUri.resetValue();
      this.fileName = "-";
      this.gameName = "-";
      this.gameRound = "-";
    } else {
      setGameData(gameData);
      ClientSetting.defaultGameName.setValue(gameData.getGameName());
      ClientSetting.defaultGameUri.setValue(uri.toString());
    }
    ClientSetting.flush();
  }

  /**
   * Loads game data by parsing a given file.
   *
   * @throws Exception If file parsing is successful and an internal {@code GameData} was set.
   */
  public void load(final File file) throws Exception {
    Preconditions.checkArgument(
        file.exists(),
        "Programming error, expected file to have already been checked to exist: "
            + file.getAbsolutePath());

    // if the file name is xml, load it as a new game
    if (file.getName().toLowerCase().endsWith("xml")) {
      load(file.toURI());
    } else {
      // try to load it as a saved game whatever the extension
      final GameData newData = GameDataManager.loadGame(file);
      newData.setSaveGameFileName(file.getName());
      setGameData(newData);
    }
  }

  public void load(final @Nullable GameData data, final @Nullable String fileName) {
    setGameData(data);
    this.fileName = fileName;
    if (data != null) {
      log.info("Loaded game: " + data.getGameName() + ", in file: " + fileName);
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
  public void clearDataButKeepGameInfo(
      final String gameName, final String gameRound, final String gameVersion) {
    synchronized (this) {
      gameData = null;
      this.gameName = gameName;
      this.gameRound = gameRound;
      this.gameVersion = gameVersion;
    }
    notifyObs();
  }

  public String getFileName() {
    return Optional.ofNullable(fileName).orElse("-");
  }

  public void setGameData(final GameData data) {
    synchronized (this) {
      if (data == null) {
        gameName = gameRound = gameVersion = "-";
      } else {
        gameName = data.getGameName();
        gameRound = "" + data.getSequence().getRound();
        gameVersion = data.getGameVersion().toString();
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
    ProAi.gameOverClearCache();
    new Thread(this::loadDefaultGameSameThread).start();
  }

  /**
   * Runs the load default game logic in same thread. Default game is the one that we loaded on
   * startup.
   */
  public void loadDefaultGameSameThread() {
    ClientSetting.defaultGameUri
        .getValue()
        // we don't want to load a game file by default that is not within the map folders we
        // can load. (ie: if a previous version of triplea was using running a game within its
        // root folder, we shouldn't open it)
        .filter(
            defaultGame ->
                defaultGame.contains(ClientFileSystemHelper.getUserRootFolder().toURI().toString()))
        .map(URI::create)
        .ifPresent(this::load);
  }
}
