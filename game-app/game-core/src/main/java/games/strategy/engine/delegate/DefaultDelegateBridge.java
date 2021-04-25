package games.strategy.engine.delegate;

import games.strategy.engine.GameOverException;
import games.strategy.engine.data.Change;
import games.strategy.engine.data.CompositeChange;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.display.IDisplay;
import games.strategy.engine.framework.AbstractGame;
import games.strategy.engine.framework.IGame;
import games.strategy.engine.framework.ServerGame;
import games.strategy.engine.history.IDelegateHistoryWriter;
import games.strategy.engine.message.MessengerException;
import games.strategy.engine.player.Player;
import games.strategy.engine.random.IRandomSource;
import games.strategy.engine.random.IRandomStats.DiceType;
import games.strategy.engine.random.RandomStats;
import games.strategy.net.websocket.ClientNetworkBridge;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.triplea.http.client.web.socket.messages.WebSocketMessage;
import org.triplea.sound.ISound;

/** Default implementation of DelegateBridge. */
@RequiredArgsConstructor
public class DefaultDelegateBridge implements IDelegateBridge {
  private final GameData gameData;
  private final IGame game;
  private final IDelegateHistoryWriter historyWriter;
  private final RandomStats randomStats;
  private final DelegateExecutionManager delegateExecutionManager;
  private final ClientNetworkBridge clientNetworkBridge;
  private IRandomSource randomSource;

  @Override
  public GameData getData() {
    return gameData;
  }

  @Override
  public GamePlayer getGamePlayer() {
    return gameData.getSequence().getStep().getPlayerId();
  }

  public void setRandomSource(final IRandomSource randomSource) {
    this.randomSource = randomSource;
  }

  /**
   * All delegates should use random data that comes from both players so that neither player
   * cheats.
   */
  @Override
  public int getRandom(
      final int max, final GamePlayer player, final DiceType diceType, final String annotation) {
    final int random = randomSource.getRandom(max, annotation);
    randomStats.addRandom(random, player, diceType);
    return random;
  }

  @Override
  public int[] getRandom(
      final int max,
      final int count,
      final GamePlayer player,
      final DiceType diceType,
      final String annotation) {
    final int[] randomValues = randomSource.getRandom(max, count, annotation);
    randomStats.addRandom(randomValues, player, diceType);
    return randomValues;
  }

  @Override
  public void addChange(final Change change) {
    if (change instanceof CompositeChange) {
      final CompositeChange c = (CompositeChange) change;
      if (c.getChanges().size() == 1) {
        addChange(c.getChanges().get(0));
        return;
      }
    }
    if (!change.isEmpty()) {
      game.addChange(change);
    }
  }

  @Override
  public String getStepName() {
    return gameData.getSequence().getStep().getName();
  }

  @Override
  public IDelegateHistoryWriter getHistoryWriter() {
    return historyWriter;
  }

  private Object getOutbound(final Object o) {
    final Class<?>[] interfaces = o.getClass().getInterfaces();
    return delegateExecutionManager.newOutboundImplementation(o, interfaces);
  }

  @Override
  public Player getRemotePlayer() {
    return getRemotePlayer(getGamePlayer());
  }

  @Override
  public Player getRemotePlayer(final GamePlayer gamePlayer) {
    try {
      final Object implementor =
          game.getMessengers().getRemote(ServerGame.getRemoteName(gamePlayer));
      return (Player) getOutbound(implementor);
    } catch (final RuntimeException e) {
      if (e.getCause() instanceof MessengerException) {
        throw new GameOverException("Game Over!");
      }
      throw e;
    }
  }

  @Override
  public IDisplay getDisplayChannelBroadcaster() {
    final Object implementor =
        game.getMessengers().getChannelBroadcaster(AbstractGame.getDisplayChannel());
    return (IDisplay) getOutbound(implementor);
  }

  @Override
  public ISound getSoundChannelBroadcaster() {
    final Object implementor =
        game.getMessengers().getChannelBroadcaster(AbstractGame.getSoundChannel());
    return (ISound) getOutbound(implementor);
  }

  @Override
  public Properties getStepProperties() {
    return gameData.getSequence().getStep().getProperties();
  }

  @Override
  public void leaveDelegateExecution() {
    delegateExecutionManager.leaveDelegateExecution();
  }

  @Override
  public void enterDelegateExecution() {
    delegateExecutionManager.enterDelegateExecution();
  }

  @Override
  public void stopGameSequence() {
    ((ServerGame) game).stopGameSequence();
  }

  @Override
  public void sendMessage(final WebSocketMessage webSocketMessage) {
    clientNetworkBridge.sendMessage(webSocketMessage);
  }
}
