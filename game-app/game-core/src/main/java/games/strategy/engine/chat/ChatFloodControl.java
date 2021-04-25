package games.strategy.engine.chat;

import java.util.HashMap;
import java.util.Map;
import org.triplea.domain.data.UserName;

/**
 * Simple flood control, only allow so many events per window of time. During each rolling time
 * window, anyone that sends more than "N" messages will be filtered until the next time window
 * begins.
 */
class ChatFloodControl {
  static final int EVENTS_PER_WINDOW = 20;
  private static final int ONE_MINUTE = 60 * 1000;
  static final int WINDOW = ONE_MINUTE;

  private final Object lock = new Object();
  private final Map<UserName, Integer> messageCount = new HashMap<>();
  private long clearTime;

  ChatFloodControl() {
    this(System.currentTimeMillis());
  }

  ChatFloodControl(final long initialClearTime) {
    clearTime = initialClearTime;
  }

  boolean allow(final UserName from, final long now) {
    synchronized (lock) {
      // reset the window
      if (now > clearTime) {
        messageCount.clear();
        clearTime = now + WINDOW;
      }
      if (!messageCount.containsKey(from)) {
        messageCount.put(from, 1);
      } else {
        messageCount.put(from, messageCount.get(from) + 1);
      }
      return messageCount.get(from) <= EVENTS_PER_WINDOW;
    }
  }
}
