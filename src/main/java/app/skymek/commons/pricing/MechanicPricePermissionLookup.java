package app.skymek.commons.pricing;

import io.smallrye.mutiny.Uni;

/**
 * Reads a taller's {@code mechanicCanViewPrices} permission. Every service that depends on this library
 * provides exactly one bean: the service that owns the permission reads its table, the others ask it.
 * A failure is treated as "not allowed".
 */
public interface MechanicPricePermissionLookup {

    Uni<Boolean> mechanicCanViewPrices(Long tallerId);
}
