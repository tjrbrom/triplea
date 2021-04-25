package games.strategy.engine.framework.startup.mc;

import games.strategy.engine.chat.ChatMessageListener;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.triplea.domain.data.UserName;
import org.triplea.http.client.lobby.game.lobby.watcher.ChatUploadParams;
import org.triplea.http.client.web.socket.client.connections.GameToLobbyConnection;
import org.triplea.java.concurrency.AsyncRunner;

/**
 * This module listens for chat messages and uploads them to lobby. The upload is done non-blocking
 * on a background thread.
 */
@Builder
@Slf4j
class ServerChatUpload implements ChatMessageListener {
  @Nonnull private final GameToLobbyConnection gameToLobbyConnection;
  @Nonnull private final UserName hostName;
  @Nonnull private final Supplier<String> gameIdSupplier;

  @Override
  public void messageReceived(final UserName fromPlayer, final String chatMessage) {
    final String gameId = gameIdSupplier.get();
    if (gameId == null) {
      // null gameId can mean we are in process of reconnecting to lobby.
      return;
    }
    AsyncRunner.runAsync(
            () ->
                gameToLobbyConnection.sendChatMessageToLobby(
                    buildUploadParams(gameId, fromPlayer, chatMessage)))
        .exceptionally(
            e ->
                // Handle this as an info level so we do not disturb the user with an error pop-up,
                // we want this to be a silent failure.
                log.info("Error sending chat message to lobby: " + e.getMessage()));
  }

  private ChatUploadParams buildUploadParams(
      final String gameId, final UserName fromPlayer, final String chatMessage) {
    return ChatUploadParams.builder()
        .fromPlayer(fromPlayer)
        .chatMessage(chatMessage)
        .gameId(gameId)
        .build();
  }

  @Override
  public void eventReceived(final String eventText) {}

  @Override
  public void slapped(final UserName from) {}

  @Override
  public void playerJoined(final String message) {}

  @Override
  public void playerLeft(final String message) {}
}
