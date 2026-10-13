package io.github.afunemma.salonbooking.salon;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffMemberRepository extends JpaRepository<StaffMember, Long> {

	/**
	 * In the order they were added, so "any staff member" bookings go to the first one
	 * who is free, which is the owner in a new salon.
	 */
	List<StaffMember> findBySalonIdOrderById(Long salonId);

}
