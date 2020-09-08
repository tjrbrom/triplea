package org.triplea.modules.moderation.ban.name;

import com.github.database.rider.core.api.dataset.DataSet;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.triplea.http.client.lobby.moderator.toolbox.banned.name.ToolboxUsernameBanClient;
import org.triplea.modules.http.AllowedUserRole;
import org.triplea.modules.http.LobbyServerTest;
import org.triplea.modules.http.ProtectedEndpointTest;

@DataSet(
    value = LobbyServerTest.LOBBY_USER_DATASET + ", integration/banned_username.yml",
    useSequenceFiltering = false)
class UsernameBanControllerIntegrationTest extends ProtectedEndpointTest<ToolboxUsernameBanClient> {

  UsernameBanControllerIntegrationTest(final URI localhost) {
    super(localhost, AllowedUserRole.MODERATOR, ToolboxUsernameBanClient::newClient);
  }

  @Test
  void removeBannedUsername() {
    verifyEndpoint(client -> client.removeUsernameBan("not nice"));
  }

  @Test
  void addBannedUsername() {
    verifyEndpoint(client -> client.addUsernameBan("new bad name " + Math.random()));
  }

  @Test
  void getBannedUsernames() {
    verifyEndpointReturningCollection(ToolboxUsernameBanClient::getUsernameBans);
  }
}
