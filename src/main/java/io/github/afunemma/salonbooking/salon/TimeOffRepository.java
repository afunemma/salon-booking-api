package io.github.afunemma.salonbooking.salon;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TimeOffRepository extends JpaRepository<TimeOff, Long> {

	/** Time off in a salon that includes the date, for anyone or the whole salon. */
	@Query("""
			select t from TimeOff t
			where t.salon.id = :salonId and t.firstDay <= :date and t.lastDay >= :date
			""")
	List<TimeOff> findOnDate(Long salonId, LocalDate date);

	/** Time off that hasn't ended yet, soonest first. */
	List<TimeOff> findBySalonIdAndLastDayGreaterThanEqualOrderByFirstDayAscIdAsc(Long salonId, LocalDate from);

}
