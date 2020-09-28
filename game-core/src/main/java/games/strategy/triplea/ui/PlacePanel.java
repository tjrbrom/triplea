package games.strategy.triplea.ui;

import games.strategy.engine.data.Change;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GameDataEvent;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.GameStep;
import games.strategy.engine.data.Territory;
import games.strategy.engine.data.Unit;
import games.strategy.engine.data.events.GameDataChangeListener;
import games.strategy.engine.player.IPlayerBridge;
import games.strategy.triplea.Properties;
import games.strategy.triplea.attachments.PlayerAttachment;
import games.strategy.triplea.delegate.GameStepPropertiesHelper;
import games.strategy.triplea.delegate.Matches;
import games.strategy.triplea.delegate.data.PlaceableUnits;
import games.strategy.triplea.delegate.remote.IAbstractPlaceDelegate;
import games.strategy.triplea.ui.panels.map.MapPanel;
import games.strategy.triplea.ui.panels.map.MapSelectionListener;
import games.strategy.triplea.util.UnitCategory;
import games.strategy.triplea.util.UnitSeparator;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.triplea.java.collections.CollectionUtils;
import org.triplea.swing.SwingComponents;

class PlacePanel extends AbstractMovePanel implements GameDataChangeListener {
  private static final long serialVersionUID = -4411301492537704785L;
  private final JLabel actionLabel = new JLabel();
  private final JLabel leftToPlaceLabel = new JLabel();
  private PlaceData placeData;

  private final SimpleUnitPanel unitsToPlacePanel;

  private GamePlayer lastPlayer;
  private boolean postProductionStep;

  private final MapSelectionListener placeMapSelectionListener =
      new DefaultMapSelectionListener() {
        @Override
        public void territorySelected(final Territory territory, final MouseDetails e) {
          if (!isActive() || (e.getButton() != MouseEvent.BUTTON1)) {
            return;
          }
          final PlaceableUnits placeableUnits = getUnitsToPlace(territory);
          final Collection<Unit> units = placeableUnits.getUnits();
          final int maxUnits = placeableUnits.getMaxUnits();
          if (units.isEmpty()) {
            return;
          }
          final UnitChooser chooser =
              new UnitChooser(units, Map.of(), false, getMap().getUiContext());
          final String messageText = "Place units in " + territory.getName();
          if (maxUnits >= 0) {
            chooser.setMaxAndShowMaxButton(maxUnits);
          }
          final Dimension screenResolution = Toolkit.getDefaultToolkit().getScreenSize();
          final int availHeight = screenResolution.height - 120;
          final int availWidth = screenResolution.width - 40;
          final JScrollPane scroll = new JScrollPane(chooser);
          scroll.setBorder(BorderFactory.createEmptyBorder());
          scroll.setPreferredSize(
              new Dimension(
                  (scroll.getPreferredSize().width > availWidth
                      ? availWidth
                      : (scroll.getPreferredSize().width
                          + (scroll.getPreferredSize().height > availHeight ? 20 : 0))),
                  (scroll.getPreferredSize().height > availHeight
                      ? availHeight
                      : (scroll.getPreferredSize().height
                          + (scroll.getPreferredSize().width > availWidth ? 26 : 0)))));
          final int option =
              JOptionPane.showOptionDialog(
                  getTopLevelAncestor(),
                  scroll,
                  messageText,
                  JOptionPane.OK_CANCEL_OPTION,
                  JOptionPane.PLAIN_MESSAGE,
                  null,
                  null,
                  null);
          if (option == JOptionPane.OK_OPTION) {
            final Collection<Unit> choosen = chooser.getSelected();
            placeData = new PlaceData(choosen, territory);
            updateUnits();
            if (choosen.containsAll(units)) {
              leftToPlaceLabel.setText("");
            }
            release();
          }
        }
      };

  PlacePanel(final GameData data, final MapPanel map, final TripleAFrame frame) {
    super(data, map, frame);
    undoableMovesPanel = new UndoablePlacementsPanel(this);
    unitsToPlacePanel = new SimpleUnitPanel(map.getUiContext());
    data.addGameDataEventListener(GameDataEvent.GAME_STEP_CHANGED, this::updateStep);
    leftToPlaceLabel.setText("Units left to place:");
  }

  private void updateStep() {
    final Collection<UnitCategory> unitsToPlace;
    final boolean showUnitsToPlace;
    final GameData data = getData();
    data.acquireReadLock();
    try {
      final GameStep step = data.getSequence().getStep();
      if (step == null) {
        return;
      }
      // Note: This doesn't use getCurrentPlayer() as that may not be updated yet.
      final GamePlayer player = step.getPlayerId();
      if (player == null) {
        return;
      }
      final boolean isNewPlayerTurn = !player.equals(lastPlayer);
      if (isNewPlayerTurn) {
        postProductionStep = false;
      }
      final Collection<Unit> playerUnits = player.getUnits();
      // If we're past the production step (even if player didn't produce anything) or
      // there are units that are available to place, show the panel (set unitsToPlace).
      showUnitsToPlace = (postProductionStep || !playerUnits.isEmpty());
      unitsToPlace = showUnitsToPlace ? UnitSeparator.categorize(playerUnits) : null;
      if (GameStep.isPurchaseOrBidStep(step.getName())) {
        postProductionStep = true;
      }
      lastPlayer = player;
      // During the place step, listen for changes to update the panel.
      if (GameStep.isPlaceStep(step.getName())) {
        data.addDataChangeListener(this);
      } else {
        data.removeDataChangeListener(this);
      }
    } finally {
      data.releaseReadLock();
    }

    SwingUtilities.invokeLater(
        () -> {
          if (showUnitsToPlace) {
            unitsToPlacePanel.setUnitsFromCategories(unitsToPlace);
            SwingComponents.redraw(unitsToPlacePanel);
          } else {
            unitsToPlacePanel.removeAll();
          }
        });
  }

  @Override
  public void gameDataChanged(final Change change) {
    final Collection<UnitCategory> unitsToPlace;
    final GameData data = getData();
    data.acquireReadLock();
    try {
      final GamePlayer player = data.getSequence().getStep().getPlayerId();
      unitsToPlace = UnitSeparator.categorize(player.getUnits());
    } finally {
      data.releaseReadLock();
    }

    SwingUtilities.invokeLater(
        () -> {
          unitsToPlacePanel.setUnitsFromCategories(unitsToPlace);
          unitsToPlacePanel.revalidate();
          unitsToPlacePanel.repaint();
        });
  }

  @Override
  protected Component getUnitScrollerPanel() {
    return new JPanel();
  }

  @Override
  public void display(final GamePlayer gamePlayer) {
    super.display(gamePlayer, " place");
  }

  private void refreshActionLabelText(final boolean bid) {
    SwingUtilities.invokeLater(
        () ->
            actionLabel.setText(getCurrentPlayer().getName() + " place" + (bid ? " for bid" : "")));
  }

  PlaceData waitForPlace(final boolean bid, final IPlayerBridge playerBridge) {
    setUp(playerBridge);
    // workaround: meant to be in setUpSpecific, but it requires a variable
    refreshActionLabelText(bid);
    waitForRelease();
    cleanUp();
    return placeData;
  }

  private PlaceableUnits getUnitsToPlace(final Territory territory) {
    getData().acquireReadLock();
    try {
      // not our territory
      if (!territory.isWater() && !territory.getOwner().equals(getCurrentPlayer())) {
        if (GameStepPropertiesHelper.isBid(getData())) {
          final PlayerAttachment pa = PlayerAttachment.get(territory.getOwner());
          if ((pa == null
                  || pa.getGiveUnitControl() == null
                  || !pa.getGiveUnitControl().contains(getCurrentPlayer()))
              && !territory
                  .getUnitCollection()
                  .anyMatch(Matches.unitIsOwnedBy(getCurrentPlayer()))) {
            return new PlaceableUnits();
          }
        } else {
          return new PlaceableUnits();
        }
      }
      // get the units that can be placed on this territory.
      Collection<Unit> units = getCurrentPlayer().getUnits();
      if (territory.isWater()) {
        if (!(Properties.getProduceFightersOnCarriers(getData())
            || Properties.getProduceNewFightersOnOldCarriers(getData())
            || Properties.getLhtrCarrierProductionRules(getData())
            || GameStepPropertiesHelper.isBid(getData()))) {
          units = CollectionUtils.getMatches(units, Matches.unitIsSea());
        } else {
          final Predicate<Unit> unitIsSeaOrCanLandOnCarrier =
              Matches.unitIsSea().or(Matches.unitCanLandOnCarrier());
          units = CollectionUtils.getMatches(units, unitIsSeaOrCanLandOnCarrier);
        }
      } else {
        units = CollectionUtils.getMatches(units, Matches.unitIsNotSea());
      }
      if (units.isEmpty()) {
        return new PlaceableUnits();
      }
      final IAbstractPlaceDelegate placeDel =
          (IAbstractPlaceDelegate) getPlayerBridge().getRemoteDelegate();
      final PlaceableUnits production = placeDel.getPlaceableUnits(units, territory);
      if (production.isError()) {
        JOptionPane.showMessageDialog(
            getTopLevelAncestor(),
            production.getErrorMessage(),
            "No units",
            JOptionPane.INFORMATION_MESSAGE);
      }
      return production;
    } finally {
      getData().releaseReadLock();
    }
  }

  @Override
  public String toString() {
    return "PlacePanel";
  }

  @Override
  protected final void cancelMoveAction() {
    getMap().showMouseCursor();
    getMap().setMouseShadowUnits(null);
  }

  @Override
  protected final void undoMoveSpecific() {
    leftToPlaceLabel.setText("Units left to place:");
    updateUnits();
  }

  @Override
  protected final void cleanUpSpecific() {
    getMap().removeMapSelectionListener(placeMapSelectionListener);
  }

  @Override
  protected final void setUpSpecific() {
    getMap().addMapSelectionListener(placeMapSelectionListener);
  }

  @Override
  protected boolean doneMoveAction() {
    if (!getCurrentPlayer().getUnitCollection().isEmpty()) {
      final int option =
          JOptionPane.showConfirmDialog(
              getTopLevelAncestor(),
              "You have not placed all your units yet.  Are you sure you want to end your turn?",
              "TripleA",
              JOptionPane.YES_NO_OPTION,
              JOptionPane.PLAIN_MESSAGE);
      if (option != JOptionPane.YES_OPTION) {
        return false;
      }
    }
    placeData = null;
    return true;
  }

  @Override
  protected boolean setCancelButton() {
    return false;
  }

  @Override
  protected final List<Component> getAdditionalButtons() {
    updateUnits();
    return Arrays.asList(SwingComponents.leftBox(leftToPlaceLabel), add(unitsToPlacePanel));
  }

  private void updateUnits() {
    final Collection<UnitCategory> unitCategories =
        UnitSeparator.categorize(getCurrentPlayer().getUnits());
    unitsToPlacePanel.setUnitsFromCategories(unitCategories);
  }
}
