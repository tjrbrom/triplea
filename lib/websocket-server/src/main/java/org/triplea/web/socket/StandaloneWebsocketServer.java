package org.triplea.web.socket;

import java.net.InetSocketAddress;
import javax.websocket.CloseReason;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

@Slf4j
public class StandaloneWebsocketServer extends WebSocketServer {
  private final GenericWebSocket genericWebSocket;

  public StandaloneWebsocketServer(
      final WebSocketMessagingBus webSocketMessagingBus, final int portToOpen) {
    this(new GenericWebSocket(webSocketMessagingBus), portToOpen);
  }

  public StandaloneWebsocketServer(final GenericWebSocket genericWebSocket, final int portToOpen) {
    super(new InetSocketAddress(portToOpen));
    this.genericWebSocket = genericWebSocket;
  }

  @Override
  public void onOpen(final WebSocket webSocket, final ClientHandshake handshake) {
    genericWebSocket.onOpen(webSocket);
  }

  @Override
  public void onClose(
      final WebSocket webSocket, final int code, final String reason, final boolean remote) {
    genericWebSocket.onClose(webSocket, new CloseReason(() -> code, reason));
  }

  @Override
  public void onMessage(final WebSocket webSocket, final String message) {
    genericWebSocket.onMessage(webSocket, message);
  }

  @Override
  public void onError(final WebSocket webSocket, final Exception exception) {
    genericWebSocket.onError(webSocket, exception);
  }

  @Override
  public void onStart() {}

  public void shutdown() {
    try {
      super.stop();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn(
          "Shutdown terminated early, game server may not be fully stopped. "
              + "Terminate any remaining java processes to ensure it is stopped.",
          e);
    }
  }
}
