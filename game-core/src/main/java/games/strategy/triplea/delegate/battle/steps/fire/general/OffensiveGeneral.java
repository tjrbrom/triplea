package games.strategy.triplea.delegate.battle.steps.fire.general;

import static games.strategy.triplea.delegate.battle.BattleState.Side.OFFENSE;
import static games.strategy.triplea.delegate.battle.BattleStepStrings.UNITS;

import games.strategy.engine.delegate.IDelegateBridge;
import games.strategy.triplea.delegate.ExecutionStack;
import games.strategy.triplea.delegate.battle.BattleActions;
import games.strategy.triplea.delegate.battle.BattleState;
import games.strategy.triplea.delegate.battle.MustFightBattle.ReturnFire;
import games.strategy.triplea.delegate.battle.steps.BattleStep;
import games.strategy.triplea.delegate.battle.steps.fire.FireRoundStepsFactory;
import games.strategy.triplea.delegate.battle.steps.fire.MainDiceRoller;
import games.strategy.triplea.delegate.battle.steps.fire.SelectMainBattleCasualties;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;

/**
 * Generates fire steps for the General battle phase for the offensive player
 *
 * <p>The General battle phase is after all special units have had their turn
 */
@AllArgsConstructor
public class OffensiveGeneral implements BattleStep {

  private static final long serialVersionUID = 5770484176987786287L;

  private static final BattleState.Side side = OFFENSE;

  protected final BattleState battleState;

  protected final BattleActions battleActions;

  @Override
  public List<String> getNames() {
    return getSteps().stream()
        .flatMap(step -> step.getNames().stream())
        .collect(Collectors.toList());
  }

  @Override
  public Order getOrder() {
    return Order.GENERAL_OFFENSIVE;
  }

  @Override
  public void execute(final ExecutionStack stack, final IDelegateBridge bridge) {
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
            FiringGroupSplitterGeneral.of(side, FiringGroupSplitterGeneral.Type.NORMAL, UNITS))
        .side(side)
        .returnFire(ReturnFire.ALL)
        .diceRoller(new MainDiceRoller())
        .casualtySelector(new SelectMainBattleCasualties())
        .build()
        .createSteps();
  }
}
