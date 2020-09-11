package games.strategy.engine.lobby.client.ui.action.player.info;

import java.awt.Component;
import java.util.stream.Collectors;
import javax.swing.JDialog;
import lombok.AllArgsConstructor;
import org.triplea.http.client.lobby.moderator.PlayerSummary;
import org.triplea.swing.JTextAreaBuilder;
import org.triplea.swing.SwingComponents;

@AllArgsConstructor
class PlayerGamesTab {
  private final PlayerSummary playerSummary;

  String getTabTitle() {
    return "In Games (" + playerSummary.getCurrentGames().size() + ")";
  }

  Component getTabContents(final JDialog dialog) {
    return SwingComponents.newJScrollPane(
        new JTextAreaBuilder()
            .readOnly()
            .text(
                playerSummary
                    .getCurrentGames() //
                    .stream()
                    .sorted()
                    .collect(Collectors.joining("\n")))
            .keyListener(SwingComponents.escapeKeyListener(dialog::dispose))
            .build());
  }
}
