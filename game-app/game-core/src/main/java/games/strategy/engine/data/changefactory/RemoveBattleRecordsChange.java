package games.strategy.engine.data.changefactory;

import games.strategy.engine.data.BattleRecordsList;
import games.strategy.engine.data.Change;
import games.strategy.engine.data.GameState;
import games.strategy.triplea.delegate.data.BattleRecords;
import java.util.Map;

class RemoveBattleRecordsChange extends Change {
  private static final long serialVersionUID = 3286634991233029854L;

  private final BattleRecords recordsToRemove;
  private final int round;

  RemoveBattleRecordsChange(final BattleRecords battleRecords, final int round) {
    this.round = round;
    // do not make a copy, this is only called from AddBattleRecordsChange, and we make a copy when
    // we
    // perform, so no need for another copy.
    recordsToRemove = battleRecords;
  }

  @Override
  protected void perform(final GameState data) {
    final Map<Integer, BattleRecords> currentRecords =
        data.getBattleRecordsList().getBattleRecordsMap();
    // make a copy else we will get a concurrent modification error
    BattleRecordsList.removeRecords(currentRecords, round, new BattleRecords(recordsToRemove));
  }

  @Override
  public Change invert() {
    return new AddBattleRecordsChange(recordsToRemove, round);
  }

  @Override
  public String toString() {
    // This only occurs when serialization went badly, or something cannot be serialized.
    if (recordsToRemove == null) {
      throw new IllegalStateException(
          "Records cannot be null (most likely caused by improper or impossible serialization)");
    }
    return "Adding Battle Records: " + recordsToRemove;
  }
}
