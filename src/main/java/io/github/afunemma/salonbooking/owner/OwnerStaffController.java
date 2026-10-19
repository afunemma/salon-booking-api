package io.github.afunemma.salonbooking.owner;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.afunemma.salonbooking.common.BusinessRuleException;
import io.github.afunemma.salonbooking.common.ConflictException;
import io.github.afunemma.salonbooking.common.NotFoundException;
import io.github.afunemma.salonbooking.salon.SalonDtos.SalonResponse;
import io.github.afunemma.salonbooking.salon.SalonDtos.StaffResponse;
import io.github.afunemma.salonbooking.salon.SalonDtos.TimeOffRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.TimeOffResponse;
import io.github.afunemma.salonbooking.salon.SalonService;
import io.github.afunemma.salonbooking.salon.TimeOffService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;

/**
 * The owner's "Staff & time off" page. Uses the same services as the API, so a schedule
 * change or time off that would strand a booking is refused here too, with the reason
 * shown on the form.
 */
@Controller
@Hidden
class OwnerStaffController {

	private static final String PAGE = "owner/staff";

	private final SalonService salonService;

	private final TimeOffService timeOffService;

	private final Clock clock;

	OwnerStaffController(SalonService salonService, TimeOffService timeOffService, Clock clock) {
		this.salonService = salonService;
		this.timeOffService = timeOffService;
		this.clock = clock;
	}

	@GetMapping("/owner/salons/{salonId}/staff")
	String page(@AuthenticationPrincipal OwnerPrincipal owner, @PathVariable Long salonId, Model model) {
		SalonResponse salon = ownedSalon(owner, salonId);
		if (!model.containsAttribute("staffForm")) {
			model.addAttribute("staffForm", StaffForm.forSalon(salon));
		}
		if (!model.containsAttribute("timeOffForm")) {
			LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
			model.addAttribute("timeOffForm", new TimeOffRequest(null, tomorrow, tomorrow));
		}
		return fillPage(owner, salon, model);
	}

	@PostMapping("/owner/salons/{salonId}/staff")
	String addStaff(@AuthenticationPrincipal OwnerPrincipal owner, @PathVariable Long salonId,
			@Valid @ModelAttribute("staffForm") StaffForm form, BindingResult errors, Model model,
			RedirectAttributes redirect) {
		if (!errors.hasErrors()) {
			try {
				salonService.addStaff(salonId, owner.userId(), form.toRequest());
				redirect.addFlashAttribute("message", form.name().strip() + " was added.");
				return "redirect:/owner/salons/" + salonId + "/staff";
			}
			catch (BusinessRuleException ex) {
				model.addAttribute("staffError", ex.getMessage());
			}
		}
		return page(owner, salonId, model);
	}

	@GetMapping("/owner/salons/{salonId}/staff/{staffId}")
	String editStaff(@AuthenticationPrincipal OwnerPrincipal owner, @PathVariable Long salonId,
			@PathVariable Long staffId, Model model) {
		SalonResponse salon = ownedSalon(owner, salonId);
		model.addAttribute("form", StaffForm.from(findStaff(salonId, staffId)));
		return fillEditPage(salon, staffId, model);
	}

	@PostMapping("/owner/salons/{salonId}/staff/{staffId}")
	String updateStaff(@AuthenticationPrincipal OwnerPrincipal owner, @PathVariable Long salonId,
			@PathVariable Long staffId, @Valid @ModelAttribute("form") StaffForm form, BindingResult errors,
			Model model, RedirectAttributes redirect) {
		SalonResponse salon = ownedSalon(owner, salonId);
		if (!errors.hasErrors()) {
			try {
				salonService.updateStaff(salonId, staffId, owner.userId(), form.toRequest());
				redirect.addFlashAttribute("message", form.name().strip() + "'s schedule was saved.");
				return "redirect:/owner/salons/" + salonId + "/staff";
			}
			catch (BusinessRuleException | ConflictException ex) {
				// e.g. a new day off or break that would strand an existing booking
				model.addAttribute("staffError", ex.getMessage());
			}
		}
		return fillEditPage(salon, staffId, model);
	}

	@PostMapping("/owner/salons/{salonId}/time-off")
	String addTimeOff(@AuthenticationPrincipal OwnerPrincipal owner, @PathVariable Long salonId,
			@Valid @ModelAttribute("timeOffForm") TimeOffRequest form, BindingResult errors, Model model,
			RedirectAttributes redirect) {
		if (!errors.hasErrors()) {
			try {
				timeOffService.add(salonId, owner.userId(), form);
				redirect.addFlashAttribute("message", "Time off added.");
				return "redirect:/owner/salons/" + salonId + "/staff";
			}
			catch (BusinessRuleException | ConflictException ex) {
				model.addAttribute("timeOffError", ex.getMessage());
			}
		}
		return page(owner, salonId, model);
	}

	@PostMapping("/owner/salons/{salonId}/time-off/{timeOffId}/delete")
	String deleteTimeOff(@AuthenticationPrincipal OwnerPrincipal owner, @PathVariable Long salonId,
			@PathVariable Long timeOffId, RedirectAttributes redirect) {
		timeOffService.delete(salonId, timeOffId, owner.userId());
		redirect.addFlashAttribute("message", "Time off removed.");
		return "redirect:/owner/salons/" + salonId + "/staff";
	}

	private String fillPage(OwnerPrincipal owner, SalonResponse salon, Model model) {
		List<StaffResponse> staff = salonService.listStaff(salon.id());
		model.addAttribute("salon", salon.name());
		model.addAttribute("salonId", salon.id());
		model.addAttribute("days", DayOption.all());
		model.addAttribute("staff", staff.stream().map(StaffRow::from).toList());
		model.addAttribute("staffChoices", staff);
		model.addAttribute("timeOff",
				timeOffService.listUpcoming(salon.id(), owner.userId()).stream().map(TimeOffRow::from).toList());
		return PAGE;
	}

	private String fillEditPage(SalonResponse salon, Long staffId, Model model) {
		model.addAttribute("salon", salon.name());
		model.addAttribute("salonId", salon.id());
		model.addAttribute("staffId", staffId);
		model.addAttribute("days", DayOption.all());
		return "owner/staff-edit";
	}

	private SalonResponse ownedSalon(OwnerPrincipal owner, Long salonId) {
		salonService.findOwnedSalon(salonId, owner.userId());
		return salonService.getSalon(salonId);
	}

	private StaffResponse findStaff(Long salonId, Long staffId) {
		return salonService.listStaff(salonId)
			.stream()
			.filter(member -> member.id().equals(staffId))
			.findFirst()
			.orElseThrow(() -> new NotFoundException("Staff member " + staffId + " not found in salon " + salonId));
	}

	/** A weekday checkbox. */
	record DayOption(DayOfWeek day, String label) {

		static List<DayOption> all() {
			return Arrays.stream(DayOfWeek.values())
				.map(day -> new DayOption(day, WorkingDays.shortName(day)))
				.toList();
		}
	}

	/** A staff member as listed: "Tue–Sat · 12:00–20:00 · break 15:00–15:30". */
	record StaffRow(Long id, String name, String days, String hours, @Nullable String breakTime) {

		static StaffRow from(StaffResponse staff) {
			LocalTime breakStart = staff.breakStartsAt();
			LocalTime breakEnd = staff.breakEndsAt();
			return new StaffRow(staff.id(), staff.name(), WorkingDays.describe(staff.workingDays()),
					staff.startsAt() + "–" + staff.endsAt(),
					breakStart != null && breakEnd != null ? breakStart + "–" + breakEnd : null);
		}
	}

	/** Time off as listed: who, and which days. */
	record TimeOffRow(Long id, String who, LocalDate firstDay, LocalDate lastDay) {

		static TimeOffRow from(TimeOffResponse timeOff) {
			String staffName = timeOff.staffName();
			return new TimeOffRow(timeOff.id(), staffName != null ? staffName : "Whole salon", timeOff.firstDay(),
					timeOff.lastDay());
		}

		public boolean isOneDay() {
			return firstDay.equals(lastDay);
		}
	}

}
