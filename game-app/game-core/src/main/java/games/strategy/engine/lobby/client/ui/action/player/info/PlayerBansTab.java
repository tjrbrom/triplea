package games.strategy.engine.lobby.client.ui.action.player.info;

import java.awt.Component;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.triplea.http.client.lobby.moderator.PlayerSummary;
import org.triplea.http.client.lobby.moderator.PlayerSummary.BanInformation;
import org.triplea.java.DateTimeFormatterUtil;
import org.triplea.swing.JTableBuilder;
import org.triplea.swing.SwingComponents;

@AllArgsConstructor
class PlayerBansTab {
  private final PlayerSummary playerSummary;

  String getTabTitle() {
    return "Bans (" + playerSummary.getBans().size() + ")";
  }

  Component getTabContents() {
    return SwingComponents.newJScrollPane(
        new JTableBuilder<BanInformation>()
            .columnNames("Name", "Date Banned", "Ban Expiry", "IP", "System ID")
            .rowData(
                playerSummary.getBans().stream()
                    .sorted(Comparator.comparing(BanInformation::getEpochMilliStartDate))
                    .collect(Collectors.toList()))
            .rowMapper(
                ban ->
                    List.of(
                        ban.getName(),
                        DateTimeFormatterUtil.formatEpochMilli(ban.getEpochMilliStartDate()),
                        DateTimeFormatterUtil.formatEpochMilli(ban.getEpochMillEndDate()),
                        ban.getIp(),
                        ban.getSystemId()))
            .build());
  }
}
