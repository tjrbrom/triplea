package org.triplea.web.socket;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.websocket.Session;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.triplea.http.client.web.socket.MessageEnvelope;
import org.triplea.http.client.web.socket.messages.MessageType;
import org.triplea.http.client.web.socket.messages.WebSocketMessage;
import org.triplea.http.client.web.socket.messages.envelopes.ServerErrorMessage;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor_ = @VisibleForTesting)
@Slf4j
public class WebSocketMessagingBus {
  public static final String MESSAGING_BUS_KEY = "messaging.bus";

  @Nonnull private final MessageBroadcaster messageBroadcaster;
  @Nonnull private final MessageSender messageSender;
  @Nonnull private final SessionSet sessionSet;

  private final List<BiConsumer<WebSocketMessagingBus, Session>> sessionClosedListeners =
      new ArrayList<>();

  @Value
  private static class MessageListener<T extends WebSocketMessage> {
    MessageType<T> messageType;
    Consumer<WebSocketMessageContext<T>> listener;
  }

  private final List<MessageListener<?>> messageListeners = new ArrayList<>();

  public WebSocketMessagingBus() {
    messageSender = new MessageSender();
    messageBroadcaster = new MessageBroadcaster(messageSender);
    sessionSet = new SessionSet();
  }

  public <X extends WebSocketMessage> void sendResponse(
      final Session session, final X responseMessage) {
    messageSender.accept(session, responseMessage.toEnvelope());
  }

  public <X extends WebSocketMessage> void broadcastMessage(final X broadcastMessage) {
    messageBroadcaster.accept(sessionSet.getSessions(), broadcastMessage.toEnvelope());
  }

  public <T extends WebSocketMessage> void addListener(
      final MessageType<T> type, final Consumer<WebSocketMessageContext<T>> listener) {
    messageListeners.add(new MessageListener<>(type, listener));
  }

  @SuppressWarnings("unchecked")
  <T extends WebSocketMessage> void onMessage(
      final Session session, final MessageEnvelope envelope) {
    determineMatchingMessageType(envelope)
        .ifPresent(
            messageType -> {
              final T payload = (T) envelope.getPayload(messageType.getPayloadType());

              getListenersForMessageTypeId(envelope.getMessageTypeId())
                  .map(messageListener -> (MessageListener<T>) messageListener)
                  .forEach(
                      messageListener ->
                          messageListener.listener.accept(
                              WebSocketMessageContext.<T>builder()
                                  .messagingBus(this)
                                  .senderSession(session)
                                  .message(payload)
                                  .build()));
            });
  }

  private Optional<MessageType<?>> determineMatchingMessageType(final MessageEnvelope envelope) {
    return messageListeners.stream()
        .filter(matchListenersWithMessageTypeId(envelope.getMessageTypeId()))
        .findAny()
        .map(messageListener -> messageListener.messageType);
  }

  private static Predicate<MessageListener<?>> matchListenersWithMessageTypeId(
      final String messageTypeId) {
    return messageListener -> messageListener.messageType.getMessageTypeId().equals(messageTypeId);
  }

  private Stream<MessageListener<?>> getListenersForMessageTypeId(final String messageTypeId) {
    return messageListeners.stream()
        .filter(
            messageListener ->
                messageListener.messageType.getMessageTypeId().equals(messageTypeId));
  }

  public void addSessionDisconnectListener(
      final BiConsumer<WebSocketMessagingBus, Session> listener) {
    sessionClosedListeners.add(listener);
  }

  void onClose(final Session session) {
    sessionSet.remove(session);
    sessionClosedListeners.forEach(listener -> listener.accept(this, session));
  }

  void onOpen(final Session session) {
    sessionSet.put(session);
  }

  void onError(final Session session, final Throwable throwable) {
    final String errorId = UUID.randomUUID().toString();
    log.error(
        "Error-id processing websocket message, returning an error message to user. "
            + "Error id: {}",
        errorId,
        throwable);

    messageSender.accept(
        session,
        new ServerErrorMessage(
                "Server error, error-id#" + errorId + ", please report this to TripleA")
            .toEnvelope());
  }
}
