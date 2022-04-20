package games.strategy.engine.framework;

import static com.google.common.base.Preconditions.checkNotNull;

import games.strategy.engine.GameOverException;
import games.strategy.engine.data.Change;
import games.strategy.engine.data.CompositeChange;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameDataEvent;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.data.PlayerManager;
import games.strategy.engine.data.changefactory.ChangeFactory;
import games.strategy.engine.delegate.AutoSave;
import games.strategy.engine.delegate.DefaultDelegateBridge;
import games.strategy.engine.delegate.DelegateExecutionManager;
import games.strategy.engine.delegate.IDelegate;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.engine.delegate.IPersistentDelegate;
import games.strategy.engine.framework.save.game.GameDataWriter;
import games.strategy.engine.framework.startup.launcher.LaunchAction;
import games.strategy.engine.framework.startup.mc.IObserverWaitingToJoin;
import games.strategy.engine.framework.startup.ui.InGameLobbyWatcherWrapper;
import games.strategy.engine.history.DelegateHistoryWriter;
import games.strategy.engine.history.Event;
import games.strategy.engine.history.EventChild;
import games.strategy.engine.history.HistoryNode;
import games.strategy.engine.history.Step;
import games.strategy.engine.message.ConnectionLostException;
import games.strategy.engine.message.IRemote;
import games.strategy.engine.message.MessageContext;
import games.strategy.engine.message.RemoteName;
import games.strategy.engine.player.Player;
import games.strategy.engine.random.IRandomSource;
import games.strategy.engine.random.IRemoteRandom;
import games.strategy.engine.random.PlainRandomSource;
import games.strategy.engine.random.RandomStats;
import games.strategy.net.INode;
import games.strategy.net.Messengers;
import games.strategy.net.websocket.ClientNetworkBridge;
import games.strategy.triplea.TripleAPlayer;
import games.strategy.triplea.delegate.DiceRoll;
import games.strategy.triplea.settings.ClientSetting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.triplea.java.Interruptibles;
import org.triplea.java.ThreadRunner;
import org.triplea.util.ExitStatus;

/** Implementation of {@link IGame} for a network server node. */
@Slf4j
public class ServerGame extends AbstractGame {
  public static final String GAME_HAS_BEEN_SAVED_PROPERTY =
      "games.strategy.engine.framework.ServerGame.GameHasBeenSaved";
  static final RemoteName SERVER_REMOTE =
      new RemoteName(
          "games.strategy.engine.framework.ServerGame.SERVER_REMOTE", IServerRemote.class);

  private final RandomStats randomStats;
  private IRandomSource randomSource = new PlainRandomSource();
  private IRandomSource delegateRandomSource;
  private final DelegateExecutionManager delegateExecutionManager = new DelegateExecutionManager();
  private InGameLobbyWatcherWrapper inGameLobbyWatcher;
  private boolean needToInitialize = true;
  private final LaunchAction launchAction;
  private final ClientNetworkBridge clientNetworkBridge;

  /**
   * When the delegate execution is stopped, we countdown on this latch to prevent the
   * startgame(...) method from returning.
   */
  private final CountDownLatch delegateExecutionStoppedLatch = new CountDownLatch(1);
  /** Has the delegate signaled that delegate execution should stop. */
  private volatile boolean delegateExecutionStopped = false;

  public ServerGame(
      final GameData data,
      final Set<Player> localPlayers,
      final Map<String, INode> remotePlayerMapping,
      final Messengers messengers,
      final ClientNetworkBridge clientNetworkBridge,
      final LaunchAction launchAction) {
    super(data, localPlayers, remotePlayerMapping, messengers, clientNetworkBridge);
    this.clientNetworkBridge = clientNetworkBridge;
    this.launchAction = launchAction;
    gameModifiedChannel =
        new IGameModifiedChannel() {
          @Override
          public void gameDataChanged(final Change change) {
            assertCorrectCaller();
            gameData.performChange(change);
            gameData.getHistory().getHistoryWriter().addChange(change);
          }

          private void assertCorrectCaller() {
            if (!MessageContext.getSender().equals(getMessengers().getServerNode())) {
              throw new IllegalStateException("Only server can change game data");
            }
          }

          @Override
          public void startHistoryEvent(final String event, final Object renderingData) {
            startHistoryEvent(event);
            if (renderingData != null) {
              setRenderingData(renderingData);
            }
          }

          @Override
          public void startHistoryEvent(final String event) {
            assertCorrectCaller();
            gameData.getHistory().getHistoryWriter().startEvent(event);
          }

          @Override
          public void addChildToEvent(final String text, final Object renderingData) {
            assertCorrectCaller();
            gameData
                .getHistory()
                .getHistoryWriter()
                .addChildToEvent(new EventChild(text, renderingData));
          }

          void setRenderingData(final Object renderingData) {
            assertCorrectCaller();
            gameData.getHistory().getHistoryWriter().setRenderingData(renderingData);
          }

          @Override
          public void stepChanged(
              final String stepName,
              final String delegateName,
              final GamePlayer player,
              final int round,
              final String displayName,
              final boolean loadedFromSavedGame) {
            assertCorrectCaller();
            if (loadedFromSavedGame) {
              return;
            }
            gameData
                .getHistory()
                .getHistoryWriter()
                .startNextStep(stepName, delegateName, player, displayName);
          }

          // nothing to do, we call this
          @Override
          public void shutDown() {}
        };
    messengers.registerChannelSubscriber(gameModifiedChannel, IGame.GAME_MODIFICATION_CHANNEL);
    setupDelegateMessaging(data);
    randomStats = new RandomStats(messengers);
    // Import dice stats from history if there is any (e.g. loading a saved game).
    importDiceStats((HistoryNode) gameData.getHistory().getRoot());
    final IServerRemote serverRemote =
        () -> GameDataWriter.writeToBytes(data, delegateExecutionManager);
    messengers.registerRemote(serverRemote, SERVER_REMOTE);
  }

  private void importDiceStats(final HistoryNode node) {
    if (node instanceof EventChild) {
      final EventChild childNode = (EventChild) node;
      if (childNode.getRenderingData() instanceof DiceRoll) {
        final DiceRoll diceRoll = (DiceRoll) childNode.getRenderingData();
        final String playerName =
            diceRoll.getPlayerName() == null
                ? DiceRoll.getPlayerNameFromAnnotation(childNode.getTitle())
                : diceRoll.getPlayerName();
        final GamePlayer gamePlayer = gameData.getPlayerList().getPlayerId(playerName);

        final int[] rolls = new int[diceRoll.size()];
        for (int i = 0; i < rolls.length; i++) {
          rolls[i] = diceRoll.getDie(i).getValue();
        }
        randomStats.addRandom(rolls, gamePlayer, RandomStats.DiceType.COMBAT);
      }
    }

    for (int i = 0; i < node.getChildCount(); i++) {
      importDiceStats((HistoryNode) node.getChildAt(i));
    }
  }

  /** Adds a new observer (non-participant) node to this server game. */
  public void addObserver(
      final IObserverWaitingToJoin blockingObserver,
      final IObserverWaitingToJoin nonBlockingObserver,
      final INode newNode) {
    try {
      if (!delegateExecutionManager.blockDelegateExecution(2000)) {
        nonBlockingObserver.cannotJoinGame("Could not block delegate execution");
        return;
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      nonBlockingObserver.cannotJoinGame(e.getMessage());
      return;
    }
    try {
      final CountDownLatch waitOnObserver = new CountDownLatch(1);
      final byte[] bytes = GameDataWriter.writeToBytes(gameData, delegateExecutionManager);
      ThreadRunner.runInNewThread(
          () -> {
            try {
              blockingObserver.joinGame(bytes, playerManager.getPlayerMapping());
              waitOnObserver.countDown();
            } catch (final Exception e) {
              if (e.getCause() instanceof ConnectionLostException) {
                log.error("Connection lost to observer while joining: " + newNode.getName(), e);
              } else {
                log.error("Failed to join game", e);
              }
            }
          });
      try {
        if (!waitOnObserver.await(
            ClientSetting.serverObserverJoinWaitTime.getValueOrThrow(), TimeUnit.SECONDS)) {
          nonBlockingObserver.cannotJoinGame("Taking too long to join.");
        }
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        nonBlockingObserver.cannotJoinGame(e.getMessage());
      }
    } catch (final Exception e) {
      log.error("Failed to join game", e);
      nonBlockingObserver.cannotJoinGame(e.getMessage());
    } finally {
      delegateExecutionManager.resumeDelegateExecution();
    }
  }

  private void setupDelegateMessaging(final GameData data) {
    for (final IDelegate delegate : data.getDelegates()) {
      addDelegateMessenger(delegate);
    }
  }

  public void addDelegateMessenger(final IDelegate delegate) {
    final Class<? extends IRemote> remoteType = delegate.getRemoteType();
    // if its null then it shouldn't be added as an IRemote
    if (remoteType == null) {
      return;
    }
    final Object wrappedDelegate =
        delegateExecutionManager.newInboundImplementation(
            delegate, new Class<?>[] {delegate.getRemoteType()});
    final RemoteName descriptor = getRemoteName(delegate);
    messengers.registerRemote(wrappedDelegate, descriptor);
  }

  public static RemoteName getRemoteName(final IDelegate delegate) {
    return new RemoteName(
        "games.strategy.engine.framework.ServerGame.DELEGATE_REMOTE." + delegate.getName(),
        delegate.getRemoteType());
  }

  public static RemoteName getRemoteName(final GamePlayer gamePlayer) {
    return new RemoteName(
        "games.strategy.engine.framework.ServerGame.PLAYER_REMOTE." + gamePlayer.getName(),
        Player.class);
  }

  public static RemoteName getRemoteRandomName(final GamePlayer gamePlayer) {
    return new RemoteName(
        "games.strategy.engine.framework.ServerGame.PLAYER_RANDOM_REMOTE" + gamePlayer.getName(),
        IRemoteRandom.class);
  }

  private GameStep getCurrentStep() {
    return gameData.getSequence().getStep();
  }

  /** Starts the game in a new thread. */
  public void startGame() {
    try {
      // we don't want to notify that the step has been saved when reloading a saved game, since
      // in fact the step hasn't changed, we are just resuming where we left off
      final boolean gameHasBeenSaved =
          gameData.getProperties().get(GAME_HAS_BEEN_SAVED_PROPERTY, false);
      if (!gameHasBeenSaved) {
        gameData.getProperties().set(GAME_HAS_BEEN_SAVED_PROPERTY, Boolean.TRUE);
      }
      startPersistentDelegates();
      if (gameHasBeenSaved) {
        runStep(true);
      }
      while (!isGameOver) {
        if (delegateExecutionStopped) {
          // the delegate has told us to stop stepping through game steps
          // don't let this method return, as this method returning signals that the game is over.
          Interruptibles.await(delegateExecutionStoppedLatch);
        } else {
          runStep(false);
        }
      }
    } catch (final GameOverException e) {
      if (!isGameOver) {
        log.error("GameOverException raised, but game is not over", e);
      }
    }
  }

  /**
   * Stops the game on this server node, subsequently stopping all client nodes. This server node
   * will then be shut down.
   */
  public void stopGame() {
    if (isGameOver) {
      log.warn("Game previously stopped, cannot stop again.");
      return;
    }

    isGameOver = true;
    delegateExecutionStoppedLatch.countDown();
    // tell the players (especially the AI's) that the game is stopping, so stop doing stuff.
    for (final Player player : gamePlayers.values()) {
      // not sure whether to put this before or after we delegate execution block, but definitely
      // before the game loader shutdown
      player.stopGame();
    }
    // block delegate execution to prevent outbound messages to the players while we shut down.
    try {
      if (!delegateExecutionManager.blockDelegateExecution(16000)) {
        log.warn("Could not stop delegate execution.");
        // Try one more time
        if (!delegateExecutionManager.blockDelegateExecution(16000)) {
          log.error("Exiting...");
          ExitStatus.FAILURE.exit();
        }
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    // shutdown
    try {
      delegateExecutionManager.setGameOver();
      getGameModifiedBroadcaster().shutDown();
      randomStats.shutDown();
      messengers.unregisterChannelSubscriber(gameModifiedChannel, IGame.GAME_MODIFICATION_CHANNEL);
      messengers.unregisterRemote(SERVER_REMOTE);
      vault.shutDown();
      for (final Player gp : gamePlayers.values()) {
        messengers.unregisterRemote(getRemoteName(gp.getGamePlayer()));
      }
      for (final IDelegate delegate : gameData.getDelegates()) {
        final Class<? extends IRemote> remoteType = delegate.getRemoteType();
        // if its null then it shouldnt be added as an IRemote
        if (remoteType == null) {
          continue;
        }
        messengers.unregisterRemote(getRemoteName(delegate));
      }
    } catch (final RuntimeException e) {
      log.error("Failed to shut down server game", e);
    } finally {
      delegateExecutionManager.resumeDelegateExecution();
    }
    gameData.getGameLoader().shutDown();
  }

  private void autoSaveBefore(final IDelegate delegate) {
    saveGame(launchAction.getAutoSaveFileUtils().getBeforeStepAutoSaveFile(delegate.getName()));
  }

  @Override
  public void saveGame(final Path file) {
    checkNotNull(file);

    final Path parentDir = file.getParent();
    if (!Files.exists(parentDir)) {
      try {
        Files.createDirectories(parentDir);
      } catch (final IOException e) {
        log.error(
            "Failed to create save game directory (or one of its ancestors): "
                + parentDir.toAbsolutePath(),
            e);
      }
    }

    GameDataWriter.writeToFile(gameData, delegateExecutionManager, file);
  }

  private void runStep(final boolean stepIsRestoredFromSavedGame) {
    if (getCurrentStep().hasReachedMaxRunCount()) {
      gameData.getSequence().next();
      return;
    }
    if (isGameOver) {
      return;
    }
    final GameStep currentStep = gameData.getSequence().getStep();
    final IDelegate currentDelegate = currentStep.getDelegate();
    if (!stepIsRestoredFromSavedGame
        && currentDelegate.getClass().isAnnotationPresent(AutoSave.class)
        && currentDelegate.getClass().getAnnotation(AutoSave.class).beforeStepStart()) {
      autoSaveBefore(currentDelegate);
    }
    startStep(stepIsRestoredFromSavedGame);
    if (!stepIsRestoredFromSavedGame
        && currentDelegate.getClass().isAnnotationPresent(AutoSave.class)
        && currentDelegate.getClass().getAnnotation(AutoSave.class).afterStepStart()) {
      autoSaveBefore(currentDelegate);
    }
    if (isGameOver) {
      return;
    }
    waitForPlayerToFinishStep();
    if (isGameOver) {
      return;
    }
    // save after the step has advanced
    // otherwise, the delegate will execute again.
    final boolean autoSaveThisDelegate =
        currentDelegate.getClass().isAnnotationPresent(AutoSave.class)
            && currentDelegate.getClass().getAnnotation(AutoSave.class).afterStepEnd();
    if (autoSaveThisDelegate && currentStep.getName().endsWith("Move")) {
      final String stepName = currentStep.getName();
      // If we are headless we don't want to include the nation in the save game because that would
      // make it too
      // difficult to load later.
      autoSaveAfter(stepName);
    }
    endStep();
    if (isGameOver) {
      return;
    }
    if (gameData.getSequence().next()) {
      gameData.getHistory().getHistoryWriter().startNextRound(gameData.getSequence().getRound());
      saveGame(
          gameData.getSequence().getRound() % 2 == 0
              ? launchAction.getAutoSaveFileUtils().getEvenRoundAutoSaveFile()
              : launchAction.getAutoSaveFileUtils().getOddRoundAutoSaveFile());
    }
    if (autoSaveThisDelegate && !currentStep.getName().endsWith("Move")) {
      autoSaveAfter(currentDelegate);
    }
  }

  private void autoSaveAfter(final String stepName) {
    final var saveUtils = launchAction.getAutoSaveFileUtils();
    saveGame(saveUtils.getAfterStepAutoSaveFile(saveUtils.getAutoSaveStepName(stepName)));
  }

  private void autoSaveAfter(final IDelegate delegate) {
    final String typeName = delegate.getClass().getTypeName();
    final String stepName =
        typeName.substring(typeName.lastIndexOf('.') + 1).replaceFirst("Delegate$", "");
    saveGame(launchAction.getAutoSaveFileUtils().getAfterStepAutoSaveFile(stepName));
  }

  private void endStep() {
    delegateExecutionManager.enterDelegateExecution();
    try {
      getCurrentStep().getDelegate().end();
    } finally {
      delegateExecutionManager.leaveDelegateExecution();
    }
    getCurrentStep().incrementRunCount();
  }

  private void startPersistentDelegates() {
    for (final IDelegate delegate : gameData.getDelegates()) {
      if (!(delegate instanceof IPersistentDelegate)) {
        continue;
      }
      final DefaultDelegateBridge bridge =
          new DefaultDelegateBridge(
              gameData,
              this,
              new DelegateHistoryWriter(messengers),
              randomStats,
              delegateExecutionManager,
              clientNetworkBridge);
      if (delegateRandomSource == null) {
        delegateRandomSource =
            (IRandomSource)
                delegateExecutionManager.newOutboundImplementation(
                    randomSource, new Class<?>[] {IRandomSource.class});
      }
      bridge.setRandomSource(delegateRandomSource);
      delegateExecutionManager.enterDelegateExecution();
      try {
        delegate.setDelegateBridgeAndPlayer(bridge);
        delegate.start();
      } finally {
        delegateExecutionManager.leaveDelegateExecution();
      }
    }
  }

  private void startStep(final boolean stepIsRestoredFromSavedGame) {
    // dont save if we just loaded
    final DefaultDelegateBridge bridge =
        new DefaultDelegateBridge(
            gameData,
            this,
            new DelegateHistoryWriter(messengers),
            randomStats,
            delegateExecutionManager,
            clientNetworkBridge);
    if (delegateRandomSource == null) {
      delegateRandomSource =
          (IRandomSource)
              delegateExecutionManager.newOutboundImplementation(
                  randomSource, new Class<?>[] {IRandomSource.class});
    }
    bridge.setRandomSource(delegateRandomSource);
    // do any initialization of game data for all players here (not based on a delegate, and should
    // not be)
    // we cannot do this the very first run through, because there are no history nodes yet. We
    // should do after first
    // node is created.
    if (needToInitialize) {
      addPlayerTypesToGameData(gamePlayers.values(), playerManager, bridge);
    }
    notifyGameStepChanged(stepIsRestoredFromSavedGame);
    delegateExecutionManager.enterDelegateExecution();
    try {
      final IDelegate delegate = getCurrentStep().getDelegate();
      delegate.setDelegateBridgeAndPlayer(bridge);
      delegate.start();
    } finally {
      delegateExecutionManager.leaveDelegateExecution();
    }
  }

  private void waitForPlayerToFinishStep() {
    final GamePlayer gamePlayer = getCurrentStep().getPlayerId();
    // no player specified for the given step
    if (gamePlayer == null) {
      return;
    }
    if (!getCurrentStep().getDelegate().delegateCurrentlyRequiresUserInput()) {
      return;
    }
    final Player player = gamePlayers.get(gamePlayer);
    if (player != null) {
      // a local player
      player.start(getCurrentStep().getName());
    } else {
      // a remote player
      final INode destination = playerManager.getNode(gamePlayer.getName());
      final IGameStepAdvancer advancer =
          (IGameStepAdvancer)
              messengers.getRemote(ClientGame.getRemoteStepAdvancerName(destination));
      advancer.startPlayerStep(getCurrentStep().getName(), gamePlayer);
    }
  }

  private void notifyGameStepChanged(final boolean loadedFromSavedGame) {
    final GameStep currentStep = getCurrentStep();
    final String stepName = currentStep.getName();
    final String delegateName = currentStep.getDelegate().getName();
    final String displayName = currentStep.getDisplayName();
    final int round = gameData.getSequence().getRound();
    final GamePlayer gamePlayer = currentStep.getPlayerId();
    gameData.fireGameDataEvent(GameDataEvent.GAME_STEP_CHANGED);
    getGameModifiedBroadcaster()
        .stepChanged(stepName, delegateName, gamePlayer, round, displayName, loadedFromSavedGame);
  }

  private void addPlayerTypesToGameData(
      final Collection<Player> localPlayers,
      final PlayerManager allPlayers,
      final IDelegateBridge bridge) {
    final GameData data = bridge.getData();
    // potential bugs with adding changes to a game that has not yet started and has no history
    // nodes yet. So wait for
    // the first delegate to start before making changes.
    if (getCurrentStep() == null || getCurrentStep().getPlayerId() == null || firstRun) {
      firstRun = false;
      return;
    }
    // we can't add a new event or add new changes if we are not in a step.
    final HistoryNode curNode = data.getHistory().getLastNode();
    if (!(curNode instanceof Step)
        && !(curNode instanceof Event)
        && !(curNode instanceof EventChild)) {
      return;
    }
    final CompositeChange change = new CompositeChange();
    final Set<String> allPlayersString = allPlayers.getPlayers();
    bridge.getHistoryWriter().startEvent("Game Loaded");
    for (final Player player : localPlayers) {
      allPlayersString.remove(player.getName());
      final boolean isHuman = player instanceof TripleAPlayer;
      bridge
          .getHistoryWriter()
          .addChildToEvent(
              player.getName()
                  + ((player.getName().endsWith("s")
                          || player.getName().endsWith("ese")
                          || player.getName().endsWith("ish"))
                      ? " are"
                      : " is")
                  + " now being played by: "
                  + player.getPlayerType().getLabel());
      final GamePlayer p = data.getPlayerList().getPlayerId(player.getName());
      final String newWhoAmI =
          ((isHuman ? "Human" : "AI") + ":" + player.getPlayerType().getLabel());
      if (!p.getWhoAmI().equals(newWhoAmI)) {
        change.add(ChangeFactory.changePlayerWhoAmIChange(p, newWhoAmI));
      }
    }
    final Iterator<String> playerIter = allPlayersString.iterator();
    while (playerIter.hasNext()) {
      final String player = playerIter.next();
      playerIter.remove();
      bridge
          .getHistoryWriter()
          .addChildToEvent(
              player
                  + ((player.endsWith("s") || player.endsWith("ese") || player.endsWith("ish"))
                      ? " are"
                      : " is")
                  + " now being played by: Human:Client");
      final GamePlayer p = data.getPlayerList().getPlayerId(player);
      final String newWhoAmI = "Human:Client";
      if (!p.getWhoAmI().equals(newWhoAmI)) {
        change.add(ChangeFactory.changePlayerWhoAmIChange(p, newWhoAmI));
      }
    }
    if (!change.isEmpty()) {
      bridge.addChange(change);
    }
    needToInitialize = false;
    if (!allPlayersString.isEmpty()) {
      throw new IllegalStateException(
          "Not all Player Types (ai/human/client) could be added to game data.");
    }
  }

  private IGameModifiedChannel getGameModifiedBroadcaster() {
    return (IGameModifiedChannel) messengers.getChannelBroadcaster(IGame.GAME_MODIFICATION_CHANNEL);
  }

  @Override
  public void addChange(final Change change) {
    // let our channel subscriber do the change, that way all changes will happen in the same thread
    getGameModifiedBroadcaster().gameDataChanged(change);
  }

  @Override
  public IRandomSource getRandomSource() {
    return randomSource;
  }

  public void setRandomSource(final IRandomSource randomSource) {
    this.randomSource = randomSource;
    delegateRandomSource = null;
  }

  public InGameLobbyWatcherWrapper getInGameLobbyWatcher() {
    return inGameLobbyWatcher;
  }

  public void setInGameLobbyWatcher(final InGameLobbyWatcherWrapper inGameLobbyWatcher) {
    this.inGameLobbyWatcher = inGameLobbyWatcher;
  }

  public void stopGameSequence() {
    delegateExecutionStopped = true;
  }

  public boolean isGameSequenceRunning() {
    return !delegateExecutionStopped;
  }
}
