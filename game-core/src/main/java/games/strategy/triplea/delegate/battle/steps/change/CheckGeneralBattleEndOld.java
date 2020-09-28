package games.strategy.triplea.delegate.battle.steps.change;

import static games.strategy.triplea.delegate.battle.BattleState.Side.DEFENSE;
import static games.strategy.triplea.delegate.battle.BattleState.Side.OFFENSE;

import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.delegate.ExecutionStack;
import games.strategy.triplea.delegate.battle.BattleActions;
import games.strategy.triplea.delegate.battle.BattleState;
import games.strategy.triplea.delegate.battle.IBattle;
import games.strategy.triplea.delegate.battle.steps.retreat.OffensiveGeneralRetreat;
import org.triplea.java.RemoveOnNextMajorRelease;

/**
 * This step was broken up into multiple steps, but old save games will only have this step so they
 * need to continue to perform the multiple steps in this one step.
 */
@RemoveOnNextMajorRelease
public class CheckGeneralBattleEndOld extends CheckGeneralBattleEnd {

  public CheckGeneralBattleEndOld(
      final BattleState battleState, final BattleActions battleActions) {
    super(battleState, battleActions);
  }

  @Override
  public void execute(final ExecutionStack stack, final IDelegateBridge bridge) {
    if (hasSideLost(OFFENSE)) {
      getBattleActions().endBattle(IBattle.WhoWon.DEFENDER, bridge);

    } else if (hasSideLost(DEFENSE)) {
      new RemoveUnprotectedUnits(getBattleState(), getBattleActions())
          .removeUnprotectedUnits(bridge, DEFENSE);
      getBattleActions().endBattle(IBattle.WhoWon.ATTACKER, bridge);

    } else if (isStalemate()) {
      if (canAttackerRetreatInStalemate()) {
        new OffensiveGeneralRetreat(getBattleState(), getBattleActions()).retreatUnits(bridge);
      }
      getBattleActions().endBattle(IBattle.WhoWon.DRAW, bridge);
    }
  }
}
