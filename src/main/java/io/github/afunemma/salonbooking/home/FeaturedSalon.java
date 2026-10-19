package io.github.afunemma.salonbooking.home;

import java.util.Optional;

/**
 * A salon the landing page can link to, so visitors can try booking straight away.
 * <p>
 * Only the live demo has one (see the {@code demo} package). The landing page asks for
 * this interface instead of knowing about demo data, so the dependency points from demo
 * to home, never back.
 */
public interface FeaturedSalon {

	Optional<Long> salonId();

}
