package io.github.afunemma.salonbooking.salon;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface SalonRepository extends JpaRepository<Salon, Long> {

	/**
	 * Loads a salon and locks its row ({@code SELECT ... FOR UPDATE}) until the
	 * transaction ends. Other transactions that ask for the same lock wait their turn.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Salon s where s.id = :id")
	Optional<Salon> findByIdForUpdate(Long id);
}
