package games.strategy.engine.delegate;

import games.strategy.engine.message.IRemote;
import java.io.Serializable;

/**
 * A section of code that implements game logic. The delegate should be deterministic. All random
 * events should be obtained through calls to the delegateBridge. Delegates make changes to gameData
 * by calling the addChange method in DelegateBridge. All delegates must have a zero argument
 * constructor, due to reflection constraints. The delegate will be initialized with a call of
 * initialize(..) before used. Delegates start executing with the start method, and stop with the
 * end message. Delegates can be made accessible to players through implementing an IRemote, and
 * will be called through RemoteMessenger.
 */
public interface IDelegate {
  /** Uses name as the internal unique name and displayName for display to users. */
  void initialize(String name, String displayName);

  /**
   * Called before the delegate will run and before "start" is called.
   *
   * @param delegateBridge the IDelegateBridge
   */
  void setDelegateBridgeAndPlayer(IDelegateBridge delegateBridge);

  /** Called before the delegate will run. */
  void start();

  /** Called before the delegate will stop running. */
  void end();

  String getName();

  String getDisplayName();

  IDelegateBridge getBridge();

  /**
   * Returns the state of the Delegate. All implementations should follow this scheme:
   *
   * <pre>
   * <code>
   *  DelegateImplementationState state = new DelegateImplementationState();
   *  state.superState = super.saveState();
   *  // add other variables to state here:
   *  return state;
   * </code>
   * </pre>
   *
   * @return state of the Delegate.
   */
  Serializable saveState();

  /**
   * Loads the delegate state. All implementations should follow this scheme:
   *
   * <pre>
   * <code>
   *  DelegateImplementationState s = (DelegateImplementationState) state;
   *  super.loadState(s.superState);
   *  // load other variables from state here
   * </code>
   * </pre>
   *
   * @param state the delegates state.
   */
  void loadState(Serializable state);

  /**
   * Returns the remote type of this delegate for use by a RemoteMessenger. (Class must be an
   * interface that extends IRemote. If the return value is null, then it indicates that this
   * delegate should not be used as in IRemote.)
   */
  Class<? extends IRemote> getRemoteType();

  /**
   * Do we have any user-interface things to do in this delegate or not? Example: In the "place
   * delegate" if we have units to place or have already placed some units then this should return
   * true, and if we have nothing to place then this should return false; Example2: In a "move
   * delegate" if we have either moved units already or have units with movement left, then this
   * should return true, and if we have no units to move or undo-move, then this should return
   * false. Because communication over the network can take a while, this should only be called from
   * the server game.
   *
   * @return should we run the delegate in order to receive user input, or not?
   */
  boolean delegateCurrentlyRequiresUserInput();
}
