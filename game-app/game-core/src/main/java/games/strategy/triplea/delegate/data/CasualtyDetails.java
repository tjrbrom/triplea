package games.strategy.triplea.delegate.data;

import games.strategy.engine.data.Unit;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.util.UnitOwner;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A casualty list that also tracks whether or not casualties should be automatically calculated.
 */
public class CasualtyDetails extends CasualtyList {
  private static final long serialVersionUID = 2261683015991514918L;
  private final boolean autoCalculated;

  /**
   * Creates new CasualtyDetails.
   *
   * @param killed killed units
   * @param damaged damaged units (Can have multiple of the same unit, to show multiple hits to that
   *     unit.)
   * @param autoCalculated whether casualties should be selected automatically
   */
  public CasualtyDetails(
      final List<Unit> killed, final List<Unit> damaged, final boolean autoCalculated) {
    super(killed, damaged);
    this.autoCalculated = autoCalculated;
  }

  public CasualtyDetails(final CasualtyList casualties, final boolean autoCalculated) {
    super(
        (casualties == null ? null : casualties.getKilled()),
        (casualties == null ? null : casualties.getDamaged()));
    this.autoCalculated = autoCalculated;
  }

  public CasualtyDetails(final boolean autoCalculated) {
    this.autoCalculated = autoCalculated;
  }

  /** Empty details, with autoCalculated as true. */
  public CasualtyDetails() {
    autoCalculated = true;
  }

  public boolean getAutoCalculated() {
    return autoCalculated;
  }

  /**
   * Ensure that any killed or damaged air units have less movement than others of the same type
   *
   * @param units should be a superset of the union of killed and damaged.
   */
  public CasualtyDetails ensureAirUnitsWithLessMovementAreTakenFirst(final Collection<Unit> units) {
    return this.ensureUnitsAreTakenFirst(
        units, Matches.unitIsAir(), Comparator.comparing(Unit::getMovementLeft));
  }

  private CasualtyDetails ensureUnitsAreTakenFirst(
      final Collection<Unit> targets,
      final Predicate<Unit> matcher,
      final Comparator<Unit> comparator) {

    final Map<UnitOwner, List<Unit>> targetsGroupedByOwnerAndType =
        targets.stream().collect(Collectors.groupingBy(UnitOwner::new, Collectors.toList()));

    final List<Unit> killed =
        ensureUnitsAreTakenFirst(
            comparator,
            targetsGroupedByOwnerAndType,
            getKilled().stream()
                .filter(matcher)
                .collect(Collectors.groupingBy(UnitOwner::new, Collectors.toList())));
    final List<Unit> damaged =
        ensureUnitsAreTakenFirst(
            comparator,
            targetsGroupedByOwnerAndType,
            getDamaged().stream()
                .filter(matcher)
                .collect(Collectors.groupingBy(UnitOwner::new, Collectors.toList())));

    // copy over the killed and damaged that don't match the matcher
    killed.addAll(getKilled().stream().filter(Predicate.not(matcher)).collect(Collectors.toList()));
    damaged.addAll(
        getDamaged().stream().filter(Predicate.not(matcher)).collect(Collectors.toList()));

    return new CasualtyDetails(killed, damaged, this.getAutoCalculated());
  }

  /**
   * sort all of the units of the same owner/type by the comparator and then take the top N units
   * (where N is the number of oldUnits)
   *
   * <p>Every key in oldUnitsGroupedByOwnerAndType should be in allUnitsGroupedByOwnerAndType and
   * the values of oldUnitsGroupedByOwnerAndType should be a subset of the values in
   * allUnitsGroupedByOwnerAndType
   */
  private List<Unit> ensureUnitsAreTakenFirst(
      final Comparator<Unit> comparator,
      final Map<UnitOwner, List<Unit>> allUnitsGroupedByOwnerAndType,
      final Map<UnitOwner, List<Unit>> oldUnitsGroupedByOwnerAndType) {

    return oldUnitsGroupedByOwnerAndType.entrySet().stream()
        .flatMap(
            entry ->
                allUnitsGroupedByOwnerAndType.get(entry.getKey()).stream()
                    .sorted(comparator)
                    .limit(entry.getValue().size()))
        .collect(Collectors.toList());
  }

  /**
   * Ensure that any killed or damaged air units have less movement than others of the same type
   *
   * @param units should be a superset of the union of killed and damaged.
   */
  public CasualtyDetails ensureUnitsWithPositiveMarineBonusAreTakenLast(
      final Collection<Unit> units) {
    return this.ensureUnitsAreTakenFirst(
        units,
        unit -> unit.getUnitAttachment().getIsMarine() != 0,
        (unit1, unit2) -> {
          // wasAmphibious marines should be removed last if marine bonus is positive, otherwise
          // should be removed first
          if (unit1.getUnitAttachment().getIsMarine() > 0) {
            return Boolean.compare(unit1.getWasAmphibious(), unit2.getWasAmphibious());
          } else {
            return Boolean.compare(unit2.getWasAmphibious(), unit1.getWasAmphibious());
          }
        });
  }
}
