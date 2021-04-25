package games.strategy.engine.lobby.moderator.toolbox.tabs.moderators;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import games.strategy.engine.lobby.moderator.toolbox.tabs.ToolboxTabModelTestUtil;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.triplea.http.client.lobby.moderator.toolbox.management.ModeratorInfo;
import org.triplea.http.client.lobby.moderator.toolbox.management.ToolboxModeratorManagementClient;
import org.triplea.java.DateTimeFormatterUtil;

@ExtendWith(MockitoExtension.class)
class ModeratorsTabModelTest {
  private static final String USERNAME = "Gar, yer not fearing me without a treasure!";
  private static final ModeratorInfo MODERATOR_INFO =
      ModeratorInfo.builder()
          .name("Ahoy, heavy-hearted faith!")
          .lastLoginEpochMillis(
              LocalDateTime.of(2020, 5, 8, 10, 54).toInstant(ZoneOffset.UTC).toEpochMilli())
          .build();

  private static final ModeratorInfo MODERATOR_INFO_WITH_NULL =
      ModeratorInfo.builder().name("Ahoy, heavy-hearted faith!").build();

  @Mock private ToolboxModeratorManagementClient toolboxModeratorManagementClient;

  private ModeratorsTabModel moderatorsTabModel;

  @BeforeAll
  static void setDateTimeFormattingToUtc() {
    DateTimeFormatterUtil.setDefaultToUtc();
  }

  @Nested
  final class FetchTableHeadersTest {
    @Test
    void adminHeaders() {
      when(toolboxModeratorManagementClient.isCurrentUserAdmin()).thenReturn(true);

      moderatorsTabModel = new ModeratorsTabModel(toolboxModeratorManagementClient);

      assertThat(moderatorsTabModel.fetchTableHeaders(), is(ModeratorsTabModel.HEADERS_FOR_ADMIN));
    }

    @Test
    void moderatorHeaders() {
      when(toolboxModeratorManagementClient.isCurrentUserAdmin()).thenReturn(false);

      moderatorsTabModel = new ModeratorsTabModel(toolboxModeratorManagementClient);

      assertThat(moderatorsTabModel.fetchTableHeaders(), is(ModeratorsTabModel.HEADERS));
    }
  }

  @Nested
  final class FetchTableData {
    @Test
    void admin() {
      when(toolboxModeratorManagementClient.isCurrentUserAdmin()).thenReturn(true);
      when(toolboxModeratorManagementClient.fetchModeratorList())
          .thenReturn(List.of(MODERATOR_INFO, MODERATOR_INFO_WITH_NULL));

      moderatorsTabModel = new ModeratorsTabModel(toolboxModeratorManagementClient);

      final List<List<String>> tableData = moderatorsTabModel.fetchTableData();

      ToolboxTabModelTestUtil.verifyTableDimensions(
          tableData, ModeratorsTabModel.HEADERS_FOR_ADMIN);
      ToolboxTabModelTestUtil.verifyTableDataAtRow(
          tableData,
          0,
          MODERATOR_INFO.getName(),
          "2020-5-8 10:54",
          ModeratorsTabModel.REMOVE_MOD_BUTTON_TEXT,
          ModeratorsTabModel.MAKE_ADMIN_BUTTON_TEXT);
      ToolboxTabModelTestUtil.verifyTableDataAtRow(
          tableData,
          1,
          MODERATOR_INFO.getName(),
          "",
          ModeratorsTabModel.REMOVE_MOD_BUTTON_TEXT,
          ModeratorsTabModel.MAKE_ADMIN_BUTTON_TEXT);
    }

    @Test
    void moderator() {
      when(toolboxModeratorManagementClient.isCurrentUserAdmin()).thenReturn(false);
      when(toolboxModeratorManagementClient.fetchModeratorList())
          .thenReturn(List.of(MODERATOR_INFO, MODERATOR_INFO_WITH_NULL));

      moderatorsTabModel = new ModeratorsTabModel(toolboxModeratorManagementClient);

      final List<List<String>> tableData = moderatorsTabModel.fetchTableData();

      ToolboxTabModelTestUtil.verifyTableDimensions(tableData, ModeratorsTabModel.HEADERS);
      ToolboxTabModelTestUtil.verifyTableDataAtRow(
          tableData, 0, MODERATOR_INFO.getName(), "2020-5-8 10:54");
      ToolboxTabModelTestUtil.verifyTableDataAtRow(tableData, 1, MODERATOR_INFO.getName(), "");
    }
  }

  @Test
  void removeMod() {
    new ModeratorsTabModel(toolboxModeratorManagementClient).removeMod(USERNAME);

    verify(toolboxModeratorManagementClient).removeMod(USERNAME);
  }

  @Test
  void addAdmin() {
    new ModeratorsTabModel(toolboxModeratorManagementClient).addAdmin(USERNAME);

    verify(toolboxModeratorManagementClient).addAdmin(USERNAME);
  }

  @Nested
  class CheckUserExistsTest {
    @Test
    void checkUserExistsPositiveCase() {
      when(toolboxModeratorManagementClient.checkUserExists(USERNAME)).thenReturn(false);

      assertThat(
          new ModeratorsTabModel(toolboxModeratorManagementClient).checkUserExists(USERNAME),
          is(false));
    }

    @Test
    void checkUserExistsNegativeCase() {
      when(toolboxModeratorManagementClient.checkUserExists(USERNAME)).thenReturn(false);

      assertThat(
          new ModeratorsTabModel(toolboxModeratorManagementClient).checkUserExists(USERNAME),
          is(false));
    }
  }

  @Test
  void addModerator() {
    new ModeratorsTabModel(toolboxModeratorManagementClient).addModerator(USERNAME);

    verify(toolboxModeratorManagementClient).addModerator(USERNAME);
  }
}
