package io.github.afunemma.salonbooking.salon;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

	List<ServiceOffering> findBySalonIdOrderByName(Long salonId);
}
