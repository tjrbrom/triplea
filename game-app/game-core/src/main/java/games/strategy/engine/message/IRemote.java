package games.strategy.engine.message;

/**
 * A marker interface, used to indicate that the interface can be used by IRemoteMessenger.
 *
 * <p>All arguments and return values to all methods of an IRemote must be serializable, since the
 * methods may be called by a remote VM.
 *
 * <p>Modifications to the parameters of an IRemote may or may not be visible to the calling object.
 *
 * <p>All methods declared by an IRemote may throw a MessengerException.
 */
public interface IRemote {}
