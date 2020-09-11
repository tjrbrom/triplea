package games.strategy.engine.data;

import games.strategy.engine.data.gameparser.GameParseException;
import java.io.Serializable;

/**
 * Behavior or data that can be attached to a game object. Permits open-ended extension of game
 * objects without modifying their code.
 */
public interface IAttachment extends Serializable, DynamicallyModifiable {

  /** Called after ALL attachments are created. */
  void validate(GameData data) throws GameParseException;

  Attachable getAttachedTo();

  void setAttachedTo(Attachable attachable);

  String getName();

  void setName(String name);
}
