package games.strategy.triplea.attachments;

import games.strategy.engine.data.NamedAttachable;
import games.strategy.engine.data.UnitType;
import games.strategy.triplea.delegate.Matches;
import java.io.Serializable;
import java.util.Comparator;

/**
 * A comparator that sorts unit types in a meaningful order.
 *
 * <p>The order is based on the following attributes of the unit type:
 *
 * <ul>
 *   <li>infrastructure
 *   <li>anti-aircraft
 *   <li>air
 *   <li>sea
 *   <li>attack power
 *   <li>name
 * </ul>
 */
public class UnitTypeComparator implements Comparator<UnitType>, Serializable {
  private static final long serialVersionUID = -7065456161376169082L;

  @Override
  public int compare(final UnitType u1, final UnitType u2) {
    return Comparator.<UnitType, UnitAttachment>comparing(
            UnitAttachment::get, Comparator.comparing(UnitAttachment::getIsInfrastructure))
        .thenComparing(Matches.unitTypeIsAaForAnything()::test)
        .thenComparing(
            UnitAttachment::get,
            Comparator.comparing(UnitAttachment::getIsAir)
                .thenComparing(UnitAttachment::getIsSea)
                .thenComparingInt(UnitAttachment::getAttack))
        .thenComparing(NamedAttachable::getName)
        .compare(u1, u2);
  }
}
