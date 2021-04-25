package games.strategy.triplea.ui;

import com.google.common.annotations.VisibleForTesting;
import games.strategy.engine.data.DefaultAttachment;
import games.strategy.engine.data.GameData;
import games.strategy.engine.data.GamePlayer;
import games.strategy.engine.data.ResourceCollection;
import games.strategy.triplea.attachments.UserActionAttachment;
import games.strategy.triplea.delegate.remote.IUserActionDelegate;
import games.strategy.triplea.ui.panels.map.MapPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.triplea.sound.ClipPlayer;
import org.triplea.sound.SoundPath;
import org.triplea.swing.JButtonBuilder;
import org.triplea.swing.SwingAction;

/** Similar to PoliticsPanel, but for UserActionAttachment/Delegate. */
public class UserActionPanel extends ActionPanel {
  private static final long serialVersionUID = -2735582890226625860L;
  private final JLabel actionLabel = new JLabel();
  private JButton selectUserActionButton = null;
  private JButton doneButton = null;
  private UserActionAttachment choice = null;
  private final TripleAFrame parent;
  private boolean firstRun = true;
  private List<UserActionAttachment> validUserActions = List.of();

  /**
   * Fires up a JDialog showing valid actions, choosing an action will release this model and
   * trigger waitForRelease().
   */
  private final Action selectUserActionAction =
      new AbstractAction("Take Action...") {
        private static final long serialVersionUID = 2389485901611958851L;

        @Override
        public void actionPerformed(final ActionEvent event) {
          final JDialog userChoiceDialog = new JDialog(parent, "Actions and Operations", true);

          final JPanel userChoicePanel = new JPanel();
          userChoicePanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
          userChoicePanel.setLayout(new GridBagLayout());

          int row = 0;
          final JScrollPane choiceScroll =
              new JScrollPane(getUserActionButtonPanel(userChoiceDialog));
          choiceScroll.setBorder(BorderFactory.createEtchedBorder());
          userChoicePanel.add(
              choiceScroll,
              new GridBagConstraints(
                  0,
                  row++,
                  2,
                  1,
                  1,
                  1,
                  GridBagConstraints.CENTER,
                  GridBagConstraints.BOTH,
                  new Insets(0, 0, 0, 0),
                  0,
                  0));

          final JButton noActionButton =
              new JButton(SwingAction.of("No Actions", e -> userChoiceDialog.setVisible(false)));
          SwingUtilities.invokeLater(noActionButton::requestFocusInWindow);
          userChoicePanel.add(
              noActionButton,
              new GridBagConstraints(
                  0,
                  row,
                  2,
                  1,
                  0.0,
                  0.0,
                  GridBagConstraints.EAST,
                  GridBagConstraints.NONE,
                  new Insets(12, 0, 0, 0),
                  0,
                  0));

          userChoiceDialog.setContentPane(userChoicePanel);
          userChoiceDialog.pack();
          userChoiceDialog.setLocationRelativeTo(parent);
          userChoiceDialog.setVisible(true);
          userChoiceDialog.dispose();
        }
      };

  UserActionPanel(final GameData data, final MapPanel map, final TripleAFrame parent) {
    super(data, map);
    this.parent = parent;
  }

  @Override
  public String toString() {
    return "Actions and Operations Panel";
  }

  @Override
  public void display(final GamePlayer gamePlayer) {
    super.display(gamePlayer);
    choice = null;
    SwingUtilities.invokeLater(
        () -> {
          removeAll();
          actionLabel.setText(gamePlayer.getName() + " Actions and Operations");
          add(actionLabel);
          selectUserActionButton = new JButton(selectUserActionAction);
          selectUserActionButton.setEnabled(false);
          add(selectUserActionButton);
          doneButton =
              new JButtonBuilder()
                  .title("Done")
                  .actionListener(this::performDone)
                  .toolTip(ActionButtons.DONE_BUTTON_TOOLTIP)
                  .enabled(false)
                  .build();
          doneButton.setEnabled(false);
          SwingUtilities.invokeLater(() -> doneButton.requestFocusInWindow());
          add(doneButton);
        });
  }

  @Override
  public void performDone() {
    if (!firstRun
        || JOptionPane.showConfirmDialog(
                JOptionPane.getFrameForComponent(UserActionPanel.this),
                "Are you sure you dont want to do anything?",
                "End Actions",
                JOptionPane.YES_NO_OPTION)
            == JOptionPane.YES_OPTION) {
      choice = null;
      release();
    }
  }

  /**
   * waits till someone calls release() and then returns the action chosen.
   *
   * @return the choice of action
   */
  UserActionAttachment waitForUserActionAction(
      final boolean firstRun, final IUserActionDelegate userActionsDelegate) {
    this.firstRun = firstRun;

    validUserActions = new ArrayList<>(userActionsDelegate.getValidActions());
    validUserActions.sort(Comparator.comparing(DefaultAttachment::getName));
    if (validUserActions.isEmpty()) {
      // No Valid User actions, do nothing
      return null;
    }

    if (this.firstRun) {
      ClipPlayer.play(SoundPath.CLIP_PHASE_USER_ACTIONS, getCurrentPlayer());
    }
    SwingUtilities.invokeLater(
        () -> {
          selectUserActionButton.setEnabled(true);
          doneButton.setEnabled(true);
          // press the user action button for us.
          selectUserActionAction.actionPerformed(null);
        });

    waitForRelease();
    return choice;
  }

  @VisibleForTesting
  static boolean canSpendResourcesOnUserActions(
      final Collection<UserActionAttachment> userActions) {
    return userActions.stream().anyMatch(userAction -> !userAction.getCostResources().isEmpty());
  }

  private JPanel getUserActionButtonPanel(final JDialog parent) {
    final JPanel userActionButtonPanel = new JPanel();
    userActionButtonPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    userActionButtonPanel.setLayout(new GridBagLayout());

    final int firstRow = 0;
    final int lastRow = validUserActions.size() - 1;
    int row = 0;
    for (final UserActionAttachment uaa : validUserActions) {
      final int topInset = (row == firstRow) ? 0 : 4;
      final int bottomInset = (row == lastRow) ? 0 : 4;
      final boolean canPlayerAffordUserAction = canPlayerAffordUserAction(getCurrentPlayer(), uaa);

      userActionButtonPanel.add(
          getOtherPlayerFlags(uaa),
          new GridBagConstraints(
              0,
              row,
              1,
              1,
              0.0,
              0.0,
              GridBagConstraints.WEST,
              GridBagConstraints.HORIZONTAL,
              new Insets(topInset, 0, bottomInset, 4),
              0,
              0));

      final JButton button =
          getMap()
              .getUiContext()
              .getResourceImageFactory()
              .getResourcesButton(
                  new ResourceCollection(getData(), uaa.getCostResources()),
                  UserActionText.getInstance().getButtonText(uaa.getText()));
      button.addActionListener(
          ae -> {
            selectUserActionButton.setEnabled(false);
            doneButton.setEnabled(false);
            validUserActions = List.of();
            choice = uaa;
            parent.setVisible(false);
            release();
          });
      button.setEnabled(canPlayerAffordUserAction);
      userActionButtonPanel.add(
          button,
          new GridBagConstraints(
              1,
              row,
              1,
              1,
              0.0,
              0.0,
              GridBagConstraints.WEST,
              GridBagConstraints.HORIZONTAL,
              new Insets(topInset, 4, bottomInset, 4),
              0,
              0));

      final JLabel descriptionLabel = getActionDescriptionLabel(uaa);
      descriptionLabel.setEnabled(canPlayerAffordUserAction);
      userActionButtonPanel.add(
          descriptionLabel,
          new GridBagConstraints(
              2,
              row,
              1,
              1,
              0.0,
              0.0,
              GridBagConstraints.WEST,
              GridBagConstraints.HORIZONTAL,
              new Insets(topInset, 4, bottomInset, 0),
              0,
              0));

      row++;
    }

    return userActionButtonPanel;
  }

  @VisibleForTesting
  static boolean canPlayerAffordUserAction(
      final GamePlayer player, final UserActionAttachment userAction) {
    return player.getResources().has(userAction.getCostResources());
  }

  /**
   * Convenient method to get a JComponent showing the flags involved in this action.
   *
   * @param uaa the User action attachment to get the "otherflags" for
   * @return a JComponent with the flags involved.
   */
  private JPanel getOtherPlayerFlags(final UserActionAttachment uaa) {
    final JPanel panel = new JPanel();
    for (final GamePlayer p : uaa.getOtherPlayers()) {
      panel.add(
          new JLabel(new ImageIcon(this.getMap().getUiContext().getFlagImageFactory().getFlag(p))));
    }
    return panel;
  }

  private static JLabel getActionDescriptionLabel(final UserActionAttachment paa) {
    final String chanceString =
        paa.getChanceToHit() >= paa.getChanceDiceSides()
            ? ""
            : "[" + paa.getChanceToHit() + "/" + paa.getChanceDiceSides() + "] ";
    return new JLabel(chanceString + UserActionText.getInstance().getDescription(paa.getText()));
  }
}
