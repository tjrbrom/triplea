package games.strategy.engine.framework;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.message.IRemote;
import games.strategy.engine.message.RemoteActionCode;

interface IGameStepAdvancer extends IRemote {
  /**
   * A server calls this methods on client game when a player starts a certain step. The method
   * should not return until the player has finished the step.
   */
  @RemoteActionCode(0)
  void startPlayerStep(String stepName, GamePlayer player);
}
