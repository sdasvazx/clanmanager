package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.entity.ParticipationPeriod;
import com.clanmanager.clanmanager.repository.MemberRepository;
import com.clanmanager.clanmanager.repository.ParticipationPeriodRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/participation-periods")
@RequiredArgsConstructor
public class ParticipationPeriodController {

    private static final LocalDate AUTO_PERIOD_START = LocalDate.of(2026, 7, 8);
    private static final int AUTO_PERIOD_DAYS = 14;
    private static final int AUTO_PERIOD_LOOKAHEAD = 6;
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    private final ParticipationPeriodRepository periodRepository;
    private final MemberRepository memberRepository;

    @GetMapping
    public List<ParticipationPeriodResponse> getPeriods() {
        ensureAutoPeriods();
        return periodRepository.findAllByOrderByPeriodIndexAsc().stream()
                .map(ParticipationPeriodResponse::from)
                .toList();
    }

    private void ensureAutoPeriods() {
        int currentIndex = Math.max(0, (int) (ChronoUnit.DAYS.between(AUTO_PERIOD_START, LocalDate.now(SEOUL_ZONE)) / AUTO_PERIOD_DAYS));
        int maxIndex = currentIndex + AUTO_PERIOD_LOOKAHEAD;
        for (int index = 0; index <= maxIndex; index++) {
            int periodIndex = index;
            if (periodRepository.findByPeriodIndex(periodIndex).isPresent()) {
                continue;
            }
            int displayIndex = index + 1;
            LocalDate startDate = AUTO_PERIOD_START.plusDays((long) index * AUTO_PERIOD_DAYS);
            LocalDate endDate = startDate.plusDays(AUTO_PERIOD_DAYS);
            periodRepository.save(ParticipationPeriod.builder()
                    .periodIndex(periodIndex)
                    .startDate(startDate)
                    .endDate(endDate)
                    .periodName(displayIndex + "회차 (" + startDate + " ~ " + endDate + ")")
                    .build());
        }
    }

    @PutMapping("/{periodIndex}")
    public ParticipationPeriodResponse savePeriod(
            @PathVariable Integer periodIndex,
            @RequestParam Long adminMemberId,
            @Valid @RequestBody ParticipationPeriodRequest request
    ) {
        Member admin = memberRepository.findById(adminMemberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 참여율 회차 이름을 수정할 수 있습니다.");
        }

        String name = request.getPeriodName() == null ? "" : request.getPeriodName().trim();
        if (name.isBlank()) {
            throw new IllegalArgumentException("회차 이름을 입력해 주세요.");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("회차 시작일과 종료일이 필요합니다.");
        }

        ParticipationPeriod period = periodRepository.findByPeriodIndex(periodIndex)
                .orElseGet(() -> ParticipationPeriod.builder()
                        .periodIndex(periodIndex)
                        .build());
        period.setStartDate(request.getStartDate());
        period.setEndDate(request.getEndDate());
        period.setPeriodName(name);
        return ParticipationPeriodResponse.from(periodRepository.save(period));
    }

    @Getter
    @Setter
    public static class ParticipationPeriodRequest {
        private LocalDate startDate;
        private LocalDate endDate;

        @NotBlank(message = "기간 이름을 입력해 주세요.")
        @Size(max = 50, message = "기간 이름은 50자 이하로 입력해 주세요.")
        private String periodName;
    }

    @Getter
    @Setter
    public static class ParticipationPeriodResponse {
        private Long periodId;
        private Integer periodIndex;
        private LocalDate startDate;
        private LocalDate endDate;
        private String periodName;

        public static ParticipationPeriodResponse from(ParticipationPeriod period) {
            ParticipationPeriodResponse response = new ParticipationPeriodResponse();
            response.setPeriodId(period.getPeriodId());
            response.setPeriodIndex(period.getPeriodIndex());
            response.setStartDate(period.getStartDate());
            response.setEndDate(period.getEndDate());
            response.setPeriodName(period.getPeriodName());
            return response;
        }
    }
}
