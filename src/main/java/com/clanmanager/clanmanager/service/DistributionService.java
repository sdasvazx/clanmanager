package com.clanmanager.clanmanager.service;

import com.clanmanager.clanmanager.dto.DistributionRequestDto;
import com.clanmanager.clanmanager.dto.DistributionPenaltyDetailDto;
import com.clanmanager.clanmanager.dto.DistributionResponseDto;
import com.clanmanager.clanmanager.dto.ParticipationResponseDto;
import com.clanmanager.clanmanager.entity.ClanVault;
import com.clanmanager.clanmanager.entity.ActivityAttendance;
import com.clanmanager.clanmanager.entity.AttendanceStatus;
import com.clanmanager.clanmanager.entity.BossParticipationRecord;
import com.clanmanager.clanmanager.entity.DistributionSnapshot;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.entity.MemberSpecHistory;
import com.clanmanager.clanmanager.entity.ParticipationPeriod;
import com.clanmanager.clanmanager.entity.VaultTransaction;
import com.clanmanager.clanmanager.entity.VaultTransactionType;
import com.clanmanager.clanmanager.repository.ClanVaultRepository;
import com.clanmanager.clanmanager.repository.ActivityAttendanceRepository;
import com.clanmanager.clanmanager.repository.BossParticipationRecordRepository;
import com.clanmanager.clanmanager.repository.DistributionSnapshotRepository;
import com.clanmanager.clanmanager.repository.MemberRepository;
import com.clanmanager.clanmanager.repository.MemberSpecHistoryRepository;
import com.clanmanager.clanmanager.repository.ParticipationPeriodRepository;
import com.clanmanager.clanmanager.repository.VaultTransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DistributionService {

    private static final Long VAULT_ID = 1L;
    private static final long ABSENCE_PENALTY_DIAMONDS = 1_000L;
    private static final Set<String> ABSENCE_PENALTY_ACTIVITIES = Set.of("결승전", "클랜수호");
    private static final List<String> CLAN_ORDER = List.of("귀신", "운좋은", "귀신Z", "로망");

    private final ParticipationService participationService;
    private final MemberRepository memberRepository;
    private final MemberSpecHistoryRepository memberSpecHistoryRepository;
    private final ParticipationPeriodRepository participationPeriodRepository;
    private final ActivityAttendanceRepository attendanceRepository;
    private final BossParticipationRecordRepository bossParticipationRecordRepository;
    private final ClanVaultRepository vaultRepository;
    private final VaultTransactionRepository transactionRepository;
    private final DistributionSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public DistributionResponseDto calculate(DistributionRequestDto request) {
        DistributionSettings settings = normalize(request);
        Map<Long, Member> memberMap = memberRepository.findByActiveTrueOrderByMemberIdAsc().stream()
                .collect(Collectors.toMap(Member::getMemberId, Function.identity()));
        ParticipationAggregationResult aggregation = getParticipationForDistribution(settings, memberMap);
        ParticipationResponseDto participation = aggregation.participation();

        List<DistributionResponseDto.ResultItemDto> baseResults = participation.getRows().stream()
                .filter(row -> memberMap.containsKey(row.getMemberId()))
                .map(row -> toBaseResult(row, memberMap.get(row.getMemberId()), settings))
                .filter(row -> settings.mode().equals("TOTAL") || CLAN_ORDER.contains(row.getClanName()))
                .toList();

        Map<String, List<DistributionResponseDto.ResultItemDto>> groups = groupResults(baseResults, settings.mode());
        List<DistributionResponseDto.ClanSummaryDto> summaries = new ArrayList<>();
        List<DistributionResponseDto.ResultItemDto> finalResults = new ArrayList<>();

        groups.forEach((clanName, rows) -> {
            long participationDiamonds = settings.mode().equals("TOTAL")
                    ? settings.totalParticipationDiamonds()
                    : settings.participationDiamonds().getOrDefault(clanName, 0L);
            long powerDiamonds = settings.mode().equals("TOTAL")
                    ? settings.totalPowerDiamonds()
                    : settings.powerDiamonds().getOrDefault(clanName, 0L);
            DistributionGroupResult groupResult = allocateGroup(clanName, rows, participationDiamonds, powerDiamonds);
            summaries.add(groupResult.summary());
            finalResults.addAll(groupResult.results());
        });

        finalResults.sort(Comparator
                .comparing((DistributionResponseDto.ResultItemDto row) -> clanOrder(row.getClanName()))
                .thenComparing(DistributionResponseDto.ResultItemDto::getFinalAmount, Comparator.reverseOrder())
                .thenComparing(DistributionResponseDto.ResultItemDto::getCharacterName));

        long allocated = finalResults.stream().mapToLong(DistributionResponseDto.ResultItemDto::getFinalAmount).sum();
        long total = summaries.stream().mapToLong(DistributionResponseDto.ClanSummaryDto::getTotalDiamonds).sum();

        return DistributionResponseDto.builder()
                .mode(settings.mode())
                .participationCut(settings.participationCut())
                .powerScoreCut(settings.powerScoreCut())
                .totalDiamonds(settings.totalDiamonds())
                .totalParticipationDiamonds(settings.totalParticipationDiamonds())
                .totalPowerDiamonds(settings.totalPowerDiamonds())
                .clanDiamonds(settings.clanDiamonds())
                .participationDiamonds(settings.participationDiamonds())
                .powerDiamonds(settings.powerDiamonds())
                .periodIds(settings.periodIds())
                .selectedPeriods(aggregation.selectedPeriods())
                .totalActivityCount(aggregation.totalActivityCount())
                .allocatedDiamonds(allocated)
                .remainingDiamonds(Math.max(0L, total - allocated))
                .readOnly(false)
                .clanSummaries(summaries)
                .results(finalResults)
                .build();
    }

    @Transactional
    public DistributionResponseDto saveSnapshot(DistributionRequestDto request) {
        Member admin = requireAdmin(request.getCreatedByMemberId());
        DistributionResponseDto response = calculate(request);
        DistributionSnapshot snapshot = snapshotRepository.save(DistributionSnapshot.builder()
                .mode(response.getMode())
                .participationCut(response.getParticipationCut())
                .powerScoreCut(response.getPowerScoreCut())
                .totalDiamonds(response.getTotalDiamonds())
                .allocatedDiamonds(response.getAllocatedDiamonds())
                .remainingDiamonds(response.getRemainingDiamonds())
                .periodIds(joinPeriodIds(response.getPeriodIds()))
                .requestJson(writeJson(request))
                .responseJson(writeJson(response))
                .createdByMemberId(admin.getMemberId())
                .createdByName(admin.getCharacterName())
                .build());
        return withSnapshotMeta(response, snapshot);
    }

    @Transactional(readOnly = true)
    public List<DistributionResponseDto.SnapshotSummaryDto> getSnapshots() {
        return snapshotRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(snapshot -> DistributionResponseDto.SnapshotSummaryDto.builder()
                        .snapshotId(snapshot.getSnapshotId())
                        .mode(snapshot.getMode())
                        .participationCut(snapshot.getParticipationCut())
                        .powerScoreCut(snapshot.getPowerScoreCut())
                        .totalDiamonds(snapshot.getTotalDiamonds())
                        .allocatedDiamonds(snapshot.getAllocatedDiamonds())
                        .remainingDiamonds(snapshot.getRemainingDiamonds())
                        .periodIds(parsePeriodIds(snapshot.getPeriodIds()))
                        .createdAt(snapshot.getCreatedAt())
                        .createdByName(snapshot.getCreatedByName())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public DistributionResponseDto getSnapshot(Long snapshotId) {
        DistributionSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 분배 히스토리입니다."));
        try {
            DistributionResponseDto response = objectMapper.readValue(snapshot.getResponseJson(), DistributionResponseDto.class);
            return withSnapshotMeta(response, snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("분배 히스토리를 읽을 수 없습니다.", e);
        }
    }

    @Transactional(readOnly = true)
    public DistributionResponseDto.ResultItemDto getLatestMemberDistribution(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        return snapshotRepository.findTopByOrderByCreatedAtDesc()
                .map(snapshot -> {
                    try {
                        DistributionResponseDto response = objectMapper.readValue(snapshot.getResponseJson(), DistributionResponseDto.class);
                        return response.getResults() == null ? null : response.getResults().stream()
                                .filter(row -> memberId.equals(row.getMemberId()))
                                .findFirst()
                                .orElse(null);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException("최신 분배 결과를 읽을 수 없습니다.", e);
                    }
                })
                .orElseGet(() -> DistributionResponseDto.ResultItemDto.builder()
                        .memberId(memberId)
                        .characterName(member.getCharacterName())
                        .finalAmount(0L)
                        .participationAmount(0L)
                        .powerAmount(0L)
                        .nonParticipationPenaltyDiamonds(0L)
                        .distributed(false)
                        .build());
    }

    @Transactional
    public DistributionResponseDto updateDistributed(Long snapshotId, Long memberId, Long adminMemberId, boolean distributed) {
        requireAdmin(adminMemberId);
        DistributionSnapshot snapshot = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 분배 히스토리입니다."));
        try {
            DistributionResponseDto response = objectMapper.readValue(snapshot.getResponseJson(), DistributionResponseDto.class);
            response.getResults().stream()
                    .filter(row -> row.getMemberId().equals(memberId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("분배 결과에서 회원을 찾을 수 없습니다."))
                    .setDistributed(distributed);
            snapshot.setResponseJson(writeJson(response));
            snapshotRepository.save(snapshot);
            return withSnapshotMeta(response, snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("분배 히스토리를 읽을 수 없습니다.", e);
        }
    }

    @Transactional
    public DistributionResponseDto depositDistributions(DistributionRequestDto request) {
        Member admin = requireAdmin(request.getCreatedByMemberId());
        DistributionResponseDto response = calculate(request);
        long totalAmount = response.getResults().stream()
                .mapToLong(DistributionResponseDto.ResultItemDto::getFinalAmount)
                .sum();
        if (totalAmount <= 0) {
            throw new IllegalArgumentException("적립할 분배금이 없습니다.");
        }

        ClanVault vault = getOrCreateVaultWithLock();
        if (vault.getBalanceDiamonds() < totalAmount) {
            throw new IllegalArgumentException("클랜금고 가용 다이아가 부족합니다. 가용 다이아: " + vault.getBalanceDiamonds());
        }
        Map<Long, Member> memberMap = memberRepository.findByActiveTrueOrderByMemberIdAsc().stream()
                .collect(Collectors.toMap(Member::getMemberId, Function.identity()));
        String memo = request.getMemo() == null || request.getMemo().isBlank()
                ? "분배금 자동 적립"
                : request.getMemo().trim();

        AtomicLong runningBalance = new AtomicLong(vault.getBalanceDiamonds());
        response.getResults().stream()
                .filter(row -> row.getFinalAmount() != null && row.getFinalAmount() > 0)
                .forEach(row -> {
                    Member target = memberMap.get(row.getMemberId());
                    if (target != null) {
                        transactionRepository.save(VaultTransaction.builder()
                                .type(VaultTransactionType.DISTRIBUTION)
                                .amountDiamonds(row.getFinalAmount())
                                .balanceAfter(runningBalance.addAndGet(-row.getFinalAmount()))
                                .targetMember(target)
                                .createdBy(admin)
                                .memo(memo + " · " + row.getCharacterName())
                                .claimed(false)
                                .claimedAt(null)
                                .build());
                    }
        });
        vault.setBalanceDiamonds(runningBalance.get());
        vaultRepository.save(vault);
        return response;
    }

    private ParticipationAggregationResult getParticipationForDistribution(
            DistributionSettings settings,
            Map<Long, Member> memberMap
    ) {
        List<ParticipationPeriod> selectedPeriods = resolveParticipationPeriods(settings.periodIds());
        Map<Long, ParticipationAggregate> aggregates = new LinkedHashMap<>();
        memberMap.values().forEach(member -> aggregates.put(member.getMemberId(), new ParticipationAggregate(member)));

        Map<Long, ActivityColumnAggregate> activityColumns = new LinkedHashMap<>();
        List<DistributionResponseDto.PeriodSummaryDto> periodSummaries = new ArrayList<>();
        LocalDate startDate = null;
        LocalDate endDate = null;
        int integratedTotalActivityCount = 0;

        for (ParticipationPeriod period : selectedPeriods) {
            ParticipationResponseDto periodParticipation = participationService.getParticipation(period.getStartDate(), period.getEndDate());
            int periodTotalActivityCount = periodParticipation.getTotalActivityCount() == null
                    ? 0
                    : periodParticipation.getTotalActivityCount();

            integratedTotalActivityCount += periodTotalActivityCount;
            for (ParticipationAggregate aggregate : aggregates.values()) {
                aggregate.addTotalActivityCount(periodTotalActivityCount);
            }

            if (periodParticipation.getRows() != null) {
                periodParticipation.getRows().forEach(row -> {
                    ParticipationAggregate aggregate = aggregates.get(row.getMemberId());
                    if (aggregate != null) {
                        aggregate.add(row);
                    }
                });
            }

            applyAutomaticAbsencePenalties(period, aggregates);

            if (periodParticipation.getActivityColumns() != null) {
                periodParticipation.getActivityColumns().forEach(column ->
                        activityColumns.computeIfAbsent(column.getActivityTypeId(), ignored -> new ActivityColumnAggregate(column))
                                .add(column.getTotalCount())
                );
            }

            periodSummaries.add(DistributionResponseDto.PeriodSummaryDto.builder()
                    .periodId(period.getPeriodId())
                    .periodIndex(period.getPeriodIndex())
                    .periodName(period.getPeriodName())
                    .startDate(period.getStartDate())
                    .endDate(period.getEndDate())
                    .totalActivityCount(periodTotalActivityCount)
                    .build());
            startDate = startDate == null || period.getStartDate().isBefore(startDate) ? period.getStartDate() : startDate;
            endDate = endDate == null || period.getEndDate().isAfter(endDate) ? period.getEndDate() : endDate;
        }

        long topAttendanceCount = aggregates.values().stream()
                .mapToLong(ParticipationAggregate::attendanceCount)
                .max()
                .orElse(0L);
        int topFinalScore = aggregates.values().stream()
                .mapToInt(ParticipationAggregate::finalParticipationScore)
                .max()
                .orElse(0);

        List<ParticipationResponseDto.ActivityColumnDto> columns = activityColumns.values().stream()
                .sorted(Comparator
                        .comparing(ActivityColumnAggregate::displayOrder)
                        .thenComparing(ActivityColumnAggregate::activityTypeId))
                .map(ActivityColumnAggregate::toDto)
                .toList();
        List<ParticipationResponseDto.ParticipationMemberDto> rows = aggregates.values().stream()
                .map(aggregate -> aggregate.toDto(topAttendanceCount, topFinalScore))
                .toList();

        return new ParticipationAggregationResult(
                ParticipationResponseDto.builder()
                        .startDate(startDate)
                        .endDate(endDate)
                        .topAttendanceCount(topAttendanceCount)
                        .totalActivityCount(integratedTotalActivityCount)
                        .topFinalScore(topFinalScore)
                        .totalMemberCount(memberMap.size())
                        .activityColumns(columns)
                        .rows(rows)
                        .build(),
                periodSummaries,
                integratedTotalActivityCount
        );
    }

    private void applyAutomaticAbsencePenalties(
            ParticipationPeriod period,
            Map<Long, ParticipationAggregate> aggregates
    ) {
        Map<String, BossParticipationRecord> penaltyEvents = new LinkedHashMap<>();
        bossParticipationRecordRepository
                .findByBossDateBetweenOrderByBossDateAscCutTimeAsc(period.getStartDate(), period.getEndDate())
                .stream()
                .filter(record -> !Boolean.FALSE.equals(record.getAttendanceApplied()))
                .filter(record -> record.getActivityType() != null)
                .filter(record -> ABSENCE_PENALTY_ACTIVITIES.contains(record.getActivityType().getTypeName()))
                .forEach(record -> penaltyEvents.putIfAbsent(
                        record.getActivityType().getActivityTypeId() + ":" + record.getBossDate(),
                        record
                ));
        if (penaltyEvents.isEmpty()) {
            return;
        }

        Set<String> attended = new HashSet<>();
        for (ActivityAttendance attendance : attendanceRepository
                .findByAttendanceDateBetweenOrderByAttendanceDateDescRecordedAtDesc(period.getStartDate(), period.getEndDate())) {
            if (attendance.getStatus() == AttendanceStatus.ATTENDED && attendance.getActivityType() != null) {
                attended.add(attendance.getMember().getMemberId() + ":"
                        + attendance.getActivityType().getActivityTypeId() + ":"
                        + attendance.getAttendanceDate());
            }
        }

        penaltyEvents.values().forEach(event -> aggregates.forEach((memberId, aggregate) -> {
            String attendanceKey = memberId + ":" + event.getActivityType().getActivityTypeId() + ":" + event.getBossDate();
            if (!attended.contains(attendanceKey)) {
                aggregate.addNonParticipationPenalty(event.getActivityType().getTypeName(), event.getBossDate());
            }
        }));
    }

    private List<ParticipationPeriod> resolveParticipationPeriods(List<Long> periodIds) {
        if (periodIds == null || periodIds.isEmpty()) {
            throw new IllegalArgumentException("분배 회차를 1개 이상 선택해 주세요.");
        }
        Map<Long, ParticipationPeriod> periodMap = participationPeriodRepository.findAllByPeriodIdIn(periodIds).stream()
                .collect(Collectors.toMap(ParticipationPeriod::getPeriodId, Function.identity()));
        List<ParticipationPeriod> selectedPeriods = periodIds.stream()
                .map(periodMap::get)
                .filter(Objects::nonNull)
                .toList();
        if (selectedPeriods.size() != periodIds.size()) {
            throw new IllegalArgumentException("선택한 분배 회차 중 존재하지 않는 회차가 있습니다.");
        }
        return selectedPeriods;
    }

    private DistributionResponseDto.ResultItemDto toBaseResult(
            ParticipationResponseDto.ParticipationMemberDto row,
            Member member,
            DistributionSettings settings
    ) {
        double currentPower = toMan(member.getCombatPower());
        double previousPower = previousPowerMan(member, currentPower);
        double growthScore = scoreBetween(previousPower, currentPower);
        double currentPowerScore = scoreBetween(0.0, currentPower);
        double powerScore = round1(growthScore + currentPowerScore);
        boolean participationEligible = nullToZero(row.getParticipationRate()) >= settings.participationCut();
        boolean powerEligible = powerCutEligible(currentPower, settings.powerScoreCut());

        return DistributionResponseDto.ResultItemDto.builder()
                .memberId(member.getMemberId())
                .characterName(member.getCharacterName())
                .clanName(canonicalClanName(member.getGuildName()))
                .characterClass(member.getCharacterClass())
                .level(member.getLevel())
                .combatPower(member.getCombatPower())
                .currentPowerMan(round1(currentPower))
                .previousPowerMan(round1(previousPower))
                .growthScore(growthScore)
                .currentPowerScore(currentPowerScore)
                .powerScore(powerScore)
                .attendanceCount(row.getAttendanceCount())
                .totalActivityCount(row.getTotalActivityCount())
                .integratedParticipationRate(row.getParticipationRate())
                .participationRate(row.getParticipationRate())
                .absencePenaltyScore(row.getAbsencePenaltyScore())
                .finalParticipationScore(row.getFinalParticipationScore())
                .participationEligible(participationEligible)
                .powerEligible(powerEligible)
                .participationAmount(0L)
                .powerAmount(0L)
                .nonParticipationPenaltyDiamonds(row.getNonParticipationPenaltyDiamonds())
                .nonParticipationPenaltyDetails(row.getNonParticipationPenaltyDetails())
                .finalAmount(0L)
                .distributed(false)
                .build();
    }

    private Map<String, List<DistributionResponseDto.ResultItemDto>> groupResults(
            List<DistributionResponseDto.ResultItemDto> results,
            String mode
    ) {
        if (mode.equals("TOTAL")) {
            return new LinkedHashMap<>(Map.of("전체", results));
        }
        Map<String, List<DistributionResponseDto.ResultItemDto>> groups = new LinkedHashMap<>();
        CLAN_ORDER.forEach(clan -> groups.put(clan, new ArrayList<>()));
        results.stream()
                .filter(row -> CLAN_ORDER.contains(row.getClanName()))
                .forEach(row -> groups.get(row.getClanName()).add(row));
        return groups;
    }

    private DistributionGroupResult allocateGroup(
            String clanName,
            List<DistributionResponseDto.ResultItemDto> rows,
            long participationPool,
            long powerPool
    ) {
        List<DistributionResponseDto.ResultItemDto> participationEligible = rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.getParticipationEligible()))
                .toList();
        List<DistributionResponseDto.ResultItemDto> powerEligible = rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.getPowerEligible()))
                .toList();
        BigDecimal totalParticipationScore = participationEligible.stream()
                .map(row -> decimal(row.getFinalParticipationScore()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPowerScore = powerEligible.stream()
                .map(row -> decimal(row.getPowerScore()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<DistributionResponseDto.ResultItemDto> allocatedRows = rows.stream()
                .map(row -> {
                    long participationAmount = Boolean.TRUE.equals(row.getParticipationEligible())
                            ? allocateByScore(row.getFinalParticipationScore(), participationPool, totalParticipationScore)
                            : 0L;
                    long powerAmount = Boolean.TRUE.equals(row.getPowerEligible())
                            ? allocateByScore(row.getPowerScore(), powerPool, totalPowerScore)
                            : 0L;
                    return copyWithAmounts(row, participationAmount, powerAmount);
                })
                .toList();
        long allocated = allocatedRows.stream().mapToLong(DistributionResponseDto.ResultItemDto::getFinalAmount).sum();
        long totalDiamonds = participationPool + powerPool;

        return new DistributionGroupResult(
                DistributionResponseDto.ClanSummaryDto.builder()
                        .clanName(clanName)
                        .memberCount(rows.size())
                        .totalDiamonds(totalDiamonds)
                        .participationPool(participationPool)
                        .powerPool(powerPool)
                        .participationEligibleCount(participationEligible.size())
                        .powerEligibleCount(powerEligible.size())
                        .allocatedDiamonds(allocated)
                        .remainingDiamonds(Math.max(0L, totalDiamonds - allocated))
                        .participationDiamondsPerPoint(diamondsPerPoint(participationPool, totalParticipationScore))
                        .powerDiamondsPerPoint(diamondsPerPoint(powerPool, totalPowerScore))
                        .build(),
                allocatedRows
        );
    }

    private DistributionResponseDto.ResultItemDto copyWithAmounts(
            DistributionResponseDto.ResultItemDto row,
            long participationAmount,
            long powerAmount
    ) {
        return DistributionResponseDto.ResultItemDto.builder()
                .memberId(row.getMemberId())
                .characterName(row.getCharacterName())
                .clanName(row.getClanName())
                .characterClass(row.getCharacterClass())
                .level(row.getLevel())
                .combatPower(row.getCombatPower())
                .currentPowerMan(row.getCurrentPowerMan())
                .previousPowerMan(row.getPreviousPowerMan())
                .growthScore(row.getGrowthScore())
                .currentPowerScore(row.getCurrentPowerScore())
                .powerScore(row.getPowerScore())
                .attendanceCount(row.getAttendanceCount())
                .totalActivityCount(row.getTotalActivityCount())
                .integratedParticipationRate(row.getIntegratedParticipationRate())
                .participationRate(row.getParticipationRate())
                .absencePenaltyScore(row.getAbsencePenaltyScore())
                .finalParticipationScore(row.getFinalParticipationScore())
                .participationEligible(row.getParticipationEligible())
                .powerEligible(row.getPowerEligible())
                .participationAmount(participationAmount)
                .powerAmount(powerAmount)
                .nonParticipationPenaltyDiamonds(row.getNonParticipationPenaltyDiamonds())
                .nonParticipationPenaltyDetails(row.getNonParticipationPenaltyDetails())
                .finalAmount(Math.max(0L, participationAmount + powerAmount - safeAmount(row.getNonParticipationPenaltyDiamonds())))
                .distributed(Boolean.TRUE.equals(row.getDistributed()))
                .build();
    }

    private DistributionSettings normalize(DistributionRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("분배 설정 정보가 필요합니다.");
        }
        String mode = request.getMode() == null ? "CLAN" : request.getMode().trim().toUpperCase();
        if (!mode.equals("TOTAL")) {
            mode = "CLAN";
        }
        List<Long> periodIds = normalizePeriodIds(request);
        Map<String, Long> clanDiamonds = new LinkedHashMap<>();
        CLAN_ORDER.forEach(clan -> clanDiamonds.put(clan, safeAmount(request.getClanDiamonds() == null ? null : request.getClanDiamonds().get(clan))));
        Map<String, Long> participationDiamonds = new LinkedHashMap<>();
        Map<String, Long> powerDiamonds = new LinkedHashMap<>();
        CLAN_ORDER.forEach(clan -> {
            long legacyAmount = clanDiamonds.getOrDefault(clan, 0L);
            participationDiamonds.put(clan, safeAmount(request.getParticipationDiamonds() == null ? legacyAmount : request.getParticipationDiamonds().get(clan)));
            powerDiamonds.put(clan, safeAmount(request.getPowerDiamonds() == null ? 0L : request.getPowerDiamonds().get(clan)));
        });
        long totalParticipationDiamonds = mode.equals("TOTAL")
                ? safeAmount(request.getTotalParticipationDiamonds() == null ? request.getTotalDiamonds() : request.getTotalParticipationDiamonds())
                : participationDiamonds.values().stream().mapToLong(Long::longValue).sum();
        long totalPowerDiamonds = mode.equals("TOTAL")
                ? safeAmount(request.getTotalPowerDiamonds())
                : powerDiamonds.values().stream().mapToLong(Long::longValue).sum();
        long totalDiamonds = totalParticipationDiamonds + totalPowerDiamonds;
        clanDiamonds.replaceAll((clan, ignored) -> participationDiamonds.getOrDefault(clan, 0L) + powerDiamonds.getOrDefault(clan, 0L));

        return new DistributionSettings(
                mode,
                Math.max(0.0, nullToZero(request.getParticipationCut())),
                Math.max(0.0, nullToZero(request.getPowerScoreCut())),
                totalDiamonds,
                clanDiamonds,
                participationDiamonds,
                powerDiamonds,
                totalParticipationDiamonds,
                totalPowerDiamonds,
                periodIds
        );
    }

    private List<Long> normalizePeriodIds(DistributionRequestDto request) {
        List<Long> ids = new ArrayList<>();
        if (request.getPeriodIds() != null) {
            request.getPeriodIds().stream()
                    .filter(Objects::nonNull)
                    .forEach(id -> {
                        if (!ids.contains(id)) {
                            ids.add(id);
                        }
                    });
        }
        if (ids.isEmpty() && request.getPeriodId() != null) {
            ids.add(request.getPeriodId());
        }
        if (ids.isEmpty() && request.getPeriodIndex() != null) {
            participationPeriodRepository.findByPeriodIndex(request.getPeriodIndex())
                    .map(ParticipationPeriod::getPeriodId)
                    .ifPresent(ids::add);
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("분배 회차를 1개 이상 선택해 주세요.");
        }
        return ids;
    }

    private Member requireAdmin(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("운영자 정보가 필요합니다.");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));
        if (member.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 사용할 수 있는 기능입니다.");
        }
        return member;
    }

    private ClanVault getOrCreateVaultWithLock() {
        return vaultRepository.findWithLockByVaultId(VAULT_ID)
                .orElseGet(() -> vaultRepository.save(ClanVault.builder()
                        .vaultId(VAULT_ID)
                        .balanceDiamonds(0L)
                        .build()));
    }

    private double previousPowerMan(Member member, double currentPower) {
        return memberSpecHistoryRepository.findTop5ByMemberIdOrderByCreatedAtDesc(member.getMemberId())
                .stream()
                .map(MemberSpecHistory::getPreviousCombatPower)
                .filter(Objects::nonNull)
                .findFirst()
                .map(this::toMan)
                .orElse(currentPower);
    }

    private boolean powerCutEligible(double currentPowerMan, double powerCut) {
        if (powerCut <= 0) {
            return true;
        }
        double thresholdMan = powerCut > 1000 ? powerCut / 10000.0 : powerCut;
        return currentPowerMan >= thresholdMan;
    }

    private double scoreBetween(double fromPower, double toPower) {
        if (toPower <= fromPower) {
            return 0.0;
        }
        double score = 0.0;
        score += segmentScore(fromPower, toPower, 0, 80, 1);
        score += segmentScore(fromPower, toPower, 80, 90, 1);
        score += segmentScore(fromPower, toPower, 90, 95, 2);
        score += segmentScore(fromPower, toPower, 95, 100, 3);
        score += segmentScore(fromPower, toPower, 100, 105, 5);
        score += segmentScore(fromPower, toPower, 105, 110, 7);
        score += segmentScore(fromPower, toPower, 110, 115, 9);
        score += segmentScore(fromPower, toPower, 115, Double.MAX_VALUE, 12);
        return round1(score);
    }

    private double segmentScore(double fromPower, double toPower, double start, double end, double pointPerMan) {
        double from = Math.max(fromPower, start);
        double to = Math.min(toPower, end);
        if (to <= from) {
            return 0.0;
        }
        return (to - from) * pointPerMan;
    }

    private double toMan(Integer combatPower) {
        if (combatPower == null || combatPower <= 0) {
            return 0.0;
        }
        return combatPower > 1000 ? combatPower / 10000.0 : combatPower.doubleValue();
    }

    private String canonicalClanName(String raw) {
        if (raw == null) {
            return "\uBBF8\uBD84\uB958";
        }
        String normalizedProbe = raw.trim().toLowerCase();
        if (normalizedProbe.isBlank()
                || normalizedProbe.equals("nan")
                || normalizedProbe.equals("none")
                || normalizedProbe.equals("non")
                || normalizedProbe.equals("null")
                || normalizedProbe.equals("-")) {
            return "\uBBF8\uBD84\uB958";
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.contains("운좋")) return "운좋은";
        if (normalized.contains("로망")) return "로망";
        if (normalized.contains("z") || normalized.contains("ｚ")) return "귀신Z";
        if (normalized.contains("귀신")) return "귀신";
        return "\uBBF8\uBD84\uB958";
    }

    private int clanOrder(String clanName) {
        int index = CLAN_ORDER.indexOf(clanName);
        return index < 0 ? CLAN_ORDER.size() : index;
    }

    private long safeAmount(Long value) {
        return value == null || value < 0 ? 0L : value;
    }

    private double nullToZero(Number value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private BigDecimal decimal(Number value) {
        return value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value.doubleValue());
    }

    private long allocateByScore(Number score, long pool, BigDecimal totalScore) {
        if (pool <= 0 || totalScore.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        return decimal(score)
                .multiply(BigDecimal.valueOf(pool))
                .divide(totalScore, 0, RoundingMode.FLOOR)
                .longValue();
    }

    private double diamondsPerPoint(long pool, BigDecimal totalScore) {
        if (pool <= 0 || totalScore.compareTo(BigDecimal.ZERO) <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(pool)
                .divide(totalScore, 2, RoundingMode.FLOOR)
                .doubleValue();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("분배 스냅샷을 저장할 수 없습니다.", e);
        }
    }

    private String joinPeriodIds(List<Long> periodIds) {
        if (periodIds == null || periodIds.isEmpty()) {
            return null;
        }
        return periodIds.stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private List<Long> parsePeriodIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String part : value.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return ids;
    }

    private DistributionResponseDto withSnapshotMeta(DistributionResponseDto response, DistributionSnapshot snapshot) {
        return DistributionResponseDto.builder()
                .snapshotId(snapshot.getSnapshotId())
                .mode(response.getMode())
                .participationCut(response.getParticipationCut())
                .powerScoreCut(response.getPowerScoreCut())
                .totalDiamonds(response.getTotalDiamonds())
                .totalParticipationDiamonds(response.getTotalParticipationDiamonds())
                .totalPowerDiamonds(response.getTotalPowerDiamonds())
                .clanDiamonds(response.getClanDiamonds())
                .participationDiamonds(response.getParticipationDiamonds())
                .powerDiamonds(response.getPowerDiamonds())
                .periodIds(response.getPeriodIds())
                .selectedPeriods(response.getSelectedPeriods())
                .totalActivityCount(response.getTotalActivityCount())
                .allocatedDiamonds(response.getAllocatedDiamonds())
                .remainingDiamonds(response.getRemainingDiamonds())
                .readOnly(true)
                .createdAt(snapshot.getCreatedAt())
                .createdByName(snapshot.getCreatedByName())
                .clanSummaries(response.getClanSummaries())
                .results(response.getResults())
                .build();
    }

    private record DistributionSettings(
            String mode,
            double participationCut,
            double powerScoreCut,
            long totalDiamonds,
            Map<String, Long> clanDiamonds,
            Map<String, Long> participationDiamonds,
            Map<String, Long> powerDiamonds,
            long totalParticipationDiamonds,
            long totalPowerDiamonds,
            List<Long> periodIds
    ) {
    }

    private record ParticipationAggregationResult(
            ParticipationResponseDto participation,
            List<DistributionResponseDto.PeriodSummaryDto> selectedPeriods,
            int totalActivityCount
    ) {
    }

    private class ParticipationAggregate {
        private final Member member;
        private long attendanceCount = 0L;
        private int totalActivityCount = 0;
        private int baseParticipationScore = 0;
        private int absencePenaltyScore = 0;
        private int minorityBonusScore = 0;
        private int finalParticipationScore = 0;
        private long nonParticipationPenaltyDiamonds = 0L;
        private final List<DistributionPenaltyDetailDto> nonParticipationPenaltyDetails = new ArrayList<>();
        private final Map<Long, Long> activityCounts = new LinkedHashMap<>();

        private ParticipationAggregate(Member member) {
            this.member = member;
        }

        private void addTotalActivityCount(int count) {
            this.totalActivityCount += Math.max(0, count);
        }

        private void add(ParticipationResponseDto.ParticipationMemberDto row) {
            this.attendanceCount += row.getAttendanceCount() == null ? 0L : row.getAttendanceCount();
            this.baseParticipationScore += row.getBaseParticipationScore() == null ? 0 : row.getBaseParticipationScore();
            this.absencePenaltyScore += row.getAbsencePenaltyScore() == null ? 0 : row.getAbsencePenaltyScore();
            this.minorityBonusScore += row.getMinorityBonusScore() == null ? 0 : row.getMinorityBonusScore();
            this.finalParticipationScore += row.getFinalParticipationScore() == null ? 0 : row.getFinalParticipationScore();
            if (row.getActivityCounts() != null) {
                row.getActivityCounts().forEach((activityTypeId, count) ->
                        this.activityCounts.merge(activityTypeId, count == null ? 0L : count, Long::sum)
                );
            }
        }

        private long attendanceCount() {
            return attendanceCount;
        }

        private int finalParticipationScore() {
            return finalParticipationScore;
        }

        private void addNonParticipationPenalty(String activityName, LocalDate missedDate) {
            this.nonParticipationPenaltyDiamonds += ABSENCE_PENALTY_DIAMONDS;
            this.nonParticipationPenaltyDetails.add(DistributionPenaltyDetailDto.builder()
                    .activityName(activityName)
                    .missedDate(missedDate)
                    .amountDiamonds(ABSENCE_PENALTY_DIAMONDS)
                    .build());
        }

        private ParticipationResponseDto.ParticipationMemberDto toDto(long topAttendanceCount, int topFinalScore) {
            double participationRate = totalActivityCount <= 0
                    ? 0.0
                    : round1((attendanceCount * 100.0) / totalActivityCount);
            double contributionRate = topFinalScore <= 0
                    ? 0.0
                    : round1((finalParticipationScore * 100.0) / topFinalScore);
            return ParticipationResponseDto.ParticipationMemberDto.builder()
                    .memberId(member.getMemberId())
                    .characterName(member.getCharacterName())
                    .guildName(member.getGuildName())
                    .characterClass(member.getCharacterClass())
                    .level(member.getLevel())
                    .combatPower(member.getCombatPower())
                    .attendanceCount(attendanceCount)
                    .topAttendanceCount(topAttendanceCount)
                    .totalActivityCount(totalActivityCount)
                    .participationRate(participationRate)
                    .baseParticipationScore(baseParticipationScore)
                    .absencePenaltyScore(absencePenaltyScore)
                    .minorityBonusScore(minorityBonusScore)
                    .finalParticipationScore(Math.max(0, finalParticipationScore))
                    .nonParticipationPenaltyDiamonds(nonParticipationPenaltyDiamonds)
                    .nonParticipationPenaltyDetails(nonParticipationPenaltyDetails)
                    .contributionRate(contributionRate)
                    .activityCounts(activityCounts)
                    .build();
        }
    }

    private static class ActivityColumnAggregate {
        private final Long activityTypeId;
        private final String activityName;
        private final Integer displayOrder;
        private final Integer participationScore;
        private final Boolean penaltyEnabled;
        private final Integer absencePenaltyScore;
        private int totalCount = 0;

        private ActivityColumnAggregate(ParticipationResponseDto.ActivityColumnDto column) {
            this.activityTypeId = column.getActivityTypeId();
            this.activityName = column.getActivityName();
            this.displayOrder = column.getDisplayOrder();
            this.participationScore = column.getParticipationScore();
            this.penaltyEnabled = column.getPenaltyEnabled();
            this.absencePenaltyScore = column.getAbsencePenaltyScore();
        }

        private void add(Integer count) {
            this.totalCount += count == null ? 0 : count;
        }

        private Long activityTypeId() {
            return activityTypeId;
        }

        private Integer displayOrder() {
            return displayOrder == null ? 0 : displayOrder;
        }

        private ParticipationResponseDto.ActivityColumnDto toDto() {
            return ParticipationResponseDto.ActivityColumnDto.builder()
                    .activityTypeId(activityTypeId)
                    .activityName(activityName)
                    .displayOrder(displayOrder)
                    .participationScore(participationScore)
                    .penaltyEnabled(penaltyEnabled)
                    .absencePenaltyScore(absencePenaltyScore)
                    .totalCount(totalCount)
                    .build();
        }
    }

    private record DistributionGroupResult(
            DistributionResponseDto.ClanSummaryDto summary,
            List<DistributionResponseDto.ResultItemDto> results
    ) {
    }
}
