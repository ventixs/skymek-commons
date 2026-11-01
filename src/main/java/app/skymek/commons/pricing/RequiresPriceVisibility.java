package app.skymek.commons.pricing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a wholly financial endpoint (payments, cash register, revenue reports): a mechanic whose taller
 * does not let them see prices gets 403 instead of a hollow response.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPriceVisibility {
}
