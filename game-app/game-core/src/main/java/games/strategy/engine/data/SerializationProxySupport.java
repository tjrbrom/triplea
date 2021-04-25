package games.strategy.engine.data;

/**
 * Annotation to mark magic 'writeReplace' methods that are used to support the serialization proxy
 * pattern. See the following references:
 *
 * <ul>
 *   <li>https://github.com/triplea-game/triplea/issues/814
 *   <li>http://blog.codefx.org/design/patterns/serialization-proxy-pattern/
 *   <li>https://dzone.com/articles/serialization-proxy-pattern
 *   <li>http://vard-lokkur.blogspot.com/2014/06/serialization-proxy-pattern-example.html
 *   <li>https://youtu.be/V1vQf4qyMXg?t=56m12s
 * </ul>
 *
 * <p>A typical usage of this annotation will and pattern will look like this: Code formatted below
 * for easy copy paste:
 *
 * <pre>
 * &#64;SerializationProxySupport
 * protected Object writeReplace() {
 *   return new SerializationProxy(this);
 * }
 *
 * private static class SerializationProxy implements Serializable {
 *   private static final long serialVersionUID = -4193924040595347947L;
 *   private final Multimap&lt;PlayerId, String> alliances;
 *
 *   // Copy constructor is okay using private API
 *   public SerializationProxy(AllianceTracker allianceTracker) {
 *     alliances = ImmutableMultimap.copyOf(allianceTracker.alliances);
 *   }
 *
 *   // This method MUST only use public APIs
 *   protected Object readResolve() {
 *     return new AllianceTracker(alliances);
 *   }
 * }
 * </pre>
 *
 * <p>IMPORTANT: It is critical that the 'readResolve' method only calls public APIs on the proxied
 * object. On the other hand, it is okay for the serialization proxy constructor to use class
 * private data members.
 *
 * <p>This way you only are using public methods to recreate your object on a read, so long as those
 * methods are left in place, we will be able to load games between versions.
 */
public @interface SerializationProxySupport {}
