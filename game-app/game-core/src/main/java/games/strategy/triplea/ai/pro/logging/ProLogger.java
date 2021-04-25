package games.strategy.triplea.ai.pro.logging;

import games.strategy.triplea.ai.pro.AbstractProAi;
import java.util.logging.Level;
import javax.annotation.Nullable;

/** Class to log messages to log window and console. */
public final class ProLogger {
  private ProLogger() {}

  public static void warn(final String message) {
    log(Level.WARNING, message);
  }

  public static void info(final String message) {
    log(Level.FINE, message);
  }

  public static void debug(final String message) {
    log(Level.FINER, message);
  }

  public static void trace(final String message) {
    log(Level.FINEST, message);
  }

  private static void log(final Level level, final String message) {
    log(level, message, null);
  }

  /**
   * Some notes on using the Pro AI logger: First, to make the logs easily readable even when there
   * are hundreds of lines, I want every considerable step down in the call stack to mean more log
   * message indentation. For example, the base logs in the {@link AbstractProAi} class have no
   * indentation before them, but the base logs in the {@link
   * games.strategy.triplea.ai.pro.ProCombatMoveAi} class will have two spaces inserted at the
   * start, and the level below that, four spaces. In this way, when you're reading the log, you can
   * skip over unimportant areas with speed because of the indentation. Second, I generally want the
   * Fine logs to be messages that run less than 10 times each round, including almost all messages
   * in the Pro AI class. Finest for messages showing details within a method that, for example,
   * returns a value. (So, for example, the NCM_Task method IsTaskWorthwhile() would primarily use
   * finest, as it just returns a boolean, and the logs within it are just for details) Finer for
   * just about everything else. (There's also the SERVER, INFO, etc. levels) Just keep these things
   * in mind while adding new logging code.
   */
  public static void log(final Level level, final String message, final @Nullable Throwable t) {
    final ProLogSettings settings = ProLogSettings.loadSettings();
    if (!settings.isLogEnabled()) {
      return; // Skip displaying to settings window if settings window option is turned off
    }
    final Level logDepth = settings.getLogLevel();
    if (logDepth.equals(Level.FINE) && (level.equals(Level.FINER) || level.equals(Level.FINEST))) {
      return; // If the settings window log depth is a higher level than this messages, skip
    }
    if (logDepth.equals(Level.FINER) && level.equals(Level.FINEST)) {
      return;
    }
    ProLogUi.notifyAiLogMessage(formatMessage(message, t, level));
  }

  /**
   * Adds extra spaces to get logs to lineup correctly. (Adds two spaces to fine, one to finer, none
   * to finest, etc.)
   */
  private static String formatMessage(
      final String message, final @Nullable Throwable t, final Level level) {
    final int compensateLength = (level.toString().length() - 4) * 2;
    final StringBuilder builder = new StringBuilder(" ".repeat(Math.max(0, compensateLength)));
    builder.append(message);
    if (t != null) {
      builder.append(" (error: ").append(t.getMessage()).append(")");
    }
    return builder.toString();
  }
}
