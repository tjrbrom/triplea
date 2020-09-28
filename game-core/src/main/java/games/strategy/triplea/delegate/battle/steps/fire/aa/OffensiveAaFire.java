package games.strategy.triplea.delegate.battle.steps.fire.aa;

import static games.strategy.triplea.delegate.battle.BattleState.Side.DEFENSE;
import static games.strategy.triplea.delegate.battle.BattleState.Side.OFFENSE;

import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.Unit;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.delegate.ExecutionStack;
import games.strategy.triplea.delegate.battle.BattleActions;
import games.strategy.triplea.delegate.battle.BattleState;
import java.util.Collection;

/** Offensive Aa units can fire and the player can select their casualties */
public class OffensiveAaFire extends AaFireAndCasualtyStep {
  private static final long serialVersionUID = 5843852442617511691L;

  public OffensiveAaFire(final BattleState battleState, final BattleActions battleActions) {
    super(battleState, battleActions);
  }

  @Override
  public Order getOrder() {
    return Order.AA_OFFENSIVE;
  }

  @Override
  public void execute(final ExecutionStack stack, final IDelegateBridge bridge) {
    if (valid()) {
      battleActions.fireOffensiveAaGuns();
    }
  }

  @Override
  GamePlayer firingPlayer() {
    return battleState.getPlayer(OFFENSE);
  }

  @Override
  GamePlayer firedAtPlayer() {
    return battleState.getPlayer(DEFENSE);
  }

  @Override
  Collection<Unit> aaGuns() {
    return battleState.getAa(OFFENSE);
  }
}
