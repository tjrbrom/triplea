package games.strategy.triplea.delegate.battle.steps.fire.firststrike;

import static games.strategy.triplea.delegate.battle.BattleState.Side.DEFENSE;
import static games.strategy.triplea.delegate.battle.BattleState.Side.OFFENSE;
import static games.strategy.triplea.delegate.battle.BattleState.UnitBattleFilter.ALIVE;
import static games.strategy.triplea.delegate.battle.BattleState.UnitBattleFilter.CASUALTY;
import static games.strategy.triplea.delegate.battle.BattleStepStrings.REMOVE_SNEAK_ATTACK_CASUALTIES;

import games.strategy.engine.data.GameData;
import games.strategy.engine.data.Unit;
import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.Properties;
import games.strategy.triplea.delegate.ExecutionStack;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.battle.BattleActions;
import games.strategy.triplea.delegate.battle.BattleState;
import games.strategy.triplea.delegate.battle.steps.BattleStep;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

public class ClearFirstStrikeCasualties implements BattleStep {

  enum State {
    SNEAK_ATTACK,
    NO_SNEAK_ATTACK,
  }

  private static final long serialVersionUID = 995118832242624142L;

  protected final BattleState battleState;

  protected final BattleActions battleActions;

  protected final State offenseState;
  protected final State defenseState;

  public ClearFirstStrikeCasualties(
      final BattleState battleState, final BattleActions battleActions) {
    this.battleState = battleState;
    this.battleActions = battleActions;
    this.offenseState = calculateOffenseState();
    this.defenseState = calculateDefenseState();
  }

  private State calculateOffenseState() {
    if (battleState.filterUnits(ALIVE, OFFENSE).stream().anyMatch(Matches.unitIsFirstStrike())) {
      final boolean canSneakAttack =
          battleState.filterUnits(ALIVE, DEFENSE).stream().noneMatch(Matches.unitIsDestroyer());
      if (canSneakAttack) {
        return State.SNEAK_ATTACK;
      }
    }
    return State.NO_SNEAK_ATTACK;
  }

  private State calculateDefenseState() {
    if (battleState.filterUnits(ALIVE, DEFENSE).stream()
        .anyMatch(Matches.unitIsFirstStrikeOnDefense(battleState.getGameData()))) {
      final GameData gameData = battleState.getGameData();
      // WWW2V2 always gives defending subs sneak attack
      final boolean canSneakAttack =
          battleState.filterUnits(ALIVE, OFFENSE).stream().noneMatch(Matches.unitIsDestroyer())
              && (Properties.getWW2V2(gameData)
                  || Properties.getDefendingSubsSneakAttack(gameData));
      if (canSneakAttack) {
        return State.SNEAK_ATTACK;
      }
    }
    return State.NO_SNEAK_ATTACK;
  }

  @Override
  public List<String> getNames() {
    final List<String> steps = new ArrayList<>();
    if (offenseHasSneakAttack() || defenseHasSneakAttack()) {
      steps.add(REMOVE_SNEAK_ATTACK_CASUALTIES);
    }
    return steps;
  }

  @Override
  public Order getOrder() {
    return Order.FIRST_STRIKE_REMOVE_CASUALTIES;
  }

  @Override
  public void execute(final ExecutionStack stack, final IDelegateBridge bridge) {
    if (!offenseHasSneakAttack() && !defenseHasSneakAttack()) {
      return;
    }

    final EnumSet<BattleState.Side> sidesToClear = getSidesToClear();
    final Collection<Unit> unitsToRemove =
        new ArrayList<>(
            battleState.filterUnits(CASUALTY, sidesToClear.toArray(new BattleState.Side[0])));
    battleActions.remove(unitsToRemove, bridge, battleState.getBattleSite(), null);
    battleState.clearWaitingToDie(sidesToClear.toArray(new BattleState.Side[0]));
  }

  private EnumSet<BattleState.Side> getSidesToClear() {
    if (Properties.getWW2V2(battleState.getGameData())) {
      // WWW2V2 subs always fire in a surprise attack phase even if their casualties will
      // be able to fire back.
      // So only clear the casualties if that side doesn't have sneak attack
      if (this.offenseState == State.SNEAK_ATTACK && this.defenseState != State.SNEAK_ATTACK) {
        return EnumSet.of(DEFENSE);
      } else if (this.defenseState == State.SNEAK_ATTACK
          && this.offenseState != State.SNEAK_ATTACK) {
        return EnumSet.of(OFFENSE);
      }
    }
    return EnumSet.of(OFFENSE, DEFENSE);
  }

  private boolean offenseHasSneakAttack() {
    return this.offenseState == State.SNEAK_ATTACK;
  }

  private boolean defenseHasSneakAttack() {
    return this.defenseState == State.SNEAK_ATTACK;
  }
}
