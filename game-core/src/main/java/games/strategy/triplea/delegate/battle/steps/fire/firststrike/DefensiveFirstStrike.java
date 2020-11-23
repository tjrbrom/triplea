package games.strategy.triplea.delegate.battle.steps.fire.firststrike;

import static games.strategy.triplea.delegate.battle.BattleState.Side.DEFENSE;
import static games.strategy.triplea.delegate.battle.BattleState.UnitBattleFilter.ALIVE;
import static games.strategy.triplea.delegate.battle.BattleStepStrings.FIRST_STRIKE_UNITS;

import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.Properties;
import games.strategy.triplea.delegate.ExecutionStack;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.battle.BattleActions;
import games.strategy.triplea.delegate.battle.BattleState;
import games.strategy.triplea.delegate.battle.MustFightBattle.ReturnFire;
import games.strategy.triplea.delegate.battle.steps.BattleStep;
import games.strategy.triplea.delegate.battle.steps.fire.FireRoundStepsFactory;
import games.strategy.triplea.delegate.battle.steps.fire.MainDiceRoller;
import games.strategy.triplea.delegate.battle.steps.fire.SelectMainBattleCasualties;
import games.strategy.triplea.delegate.battle.steps.fire.general.FiringGroupSplitterGeneral;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Generates fire steps for the first strike battle phase for the defensive player */
public class DefensiveFirstStrike implements BattleStep {

  private enum State {
    NOT_APPLICABLE,
    REGULAR,
    FIRST_STRIKE,
  }

  private static final long serialVersionUID = 3646211932844911163L;

  private static final BattleState.Side side = DEFENSE;

  protected final BattleState battleState;

  protected final BattleActions battleActions;

  protected final State state;

  protected transient ReturnFire returnFire = ReturnFire.ALL;

  public DefensiveFirstStrike(final BattleState battleState, final BattleActions battleActions) {
    this.battleState = battleState;
    this.battleActions = battleActions;
    this.state = calculateState();
  }

  /** Constructor for save compatibility */
  public DefensiveFirstStrike(
      final BattleState battleState,
      final BattleActions battleActions,
      final ReturnFire returnFire) {
    this.battleState = battleState;
    this.battleActions = battleActions;
    this.state = State.FIRST_STRIKE;
    this.returnFire = returnFire;
  }

  private State calculateState() {
    if (battleState.filterUnits(ALIVE, side).stream()
        .noneMatch(Matches.unitIsFirstStrikeOnDefense(battleState.getGameData().getProperties()))) {
      return State.NOT_APPLICABLE;
    }

    // ww2v2 rules require subs to always fire in a sub phase
    if (Properties.getWW2V2(battleState.getGameData().getProperties())) {
      return State.FIRST_STRIKE;
    }

    final boolean canSneakAttack =
        battleState.filterUnits(ALIVE, side.getOpposite()).stream()
                .noneMatch(Matches.unitIsDestroyer())
            && Properties.getDefendingSubsSneakAttack(battleState.getGameData().getProperties());
    if (canSneakAttack) {
      return State.FIRST_STRIKE;
    }
    return State.REGULAR;
  }

  @Override
  public List<String> getNames() {
    return this.state == State.NOT_APPLICABLE
        ? List.of()
        : getSteps().stream()
            .flatMap(step -> step.getNames().stream())
            .collect(Collectors.toList());
  }

  @Override
  public Order getOrder() {
    if (this.state == State.REGULAR) {
      return Order.FIRST_STRIKE_DEFENSIVE_REGULAR;
    }
    return Order.FIRST_STRIKE_DEFENSIVE;
  }

  @Override
  public void execute(final ExecutionStack stack, final IDelegateBridge bridge) {
    if (this.state == State.NOT_APPLICABLE) {
      return;
    }
    final List<BattleStep> steps = getSteps();

    // steps go in reverse order on the stack
    Collections.reverse(steps);
    steps.forEach(stack::push);
  }

  private List<BattleStep> getSteps() {
    return FireRoundStepsFactory.builder()
        .battleState(battleState)
        .battleActions(battleActions)
        .firingGroupSplitter(
            FiringGroupSplitterGeneral.of(
                side, FiringGroupSplitterGeneral.Type.FIRST_STRIKE, FIRST_STRIKE_UNITS))
        .side(side)
        .returnFire(returnFire)
        .diceRoller(new MainDiceRoller())
        .casualtySelector(new SelectMainBattleCasualties())
        .build()
        .createSteps();
  }
}
