package app.skymek.commons.pricing;

/**
 * Knows which of a service's response types carry money and how to strip it. Every service that depends
 * on this library provides exactly one bean.
 */
public interface PriceRedactor {

    /** Whether the entity is, or contains, a response type that carries money. */
    boolean carriesPrices(Object entity);

    /** A copy of the entity with every money field removed; unknown types come back unchanged. */
    Object redact(Object entity);
}
