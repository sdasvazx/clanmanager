package com.clanmanager.clanmanager.service;

import com.clanmanager.clanmanager.dto.DistributionRequestDto;
import com.clanmanager.clanmanager.dto.DistributionResponseDto;
import com.clanmanager.clanmanager.dto.ParticipationResponseDto;
import com.clanmanager.clanmanager.entity.ClanVault;
import com.clanmanager.clanmanager.entity.DistributionSnapshot;
import com.clanmanager.clanmanager.entity.Member;
import com.clanmanager.clanmanager.entity.MemberRole;
import com.clanmanager.clanmanager.entity.MemberSpecHistory;
import com.clanmanager.clanmanager.entity.ParticipationPeriod;
import com.clanmanager.clanmanager.entity.VaultTransaction;
import com.clanmanager.clanmanager.entity.VaultTransactionType;
import com.clanmanager.clanmanager.repository.ClanVaultRepository;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DistributionService {

    private static final Long VAULT_ID = 1L;
    private static final List<String> CLAN_ORDER = List.of("귀신", "운좋은", "귀신Z", "로망");

    private final ParticipationService participationService;
    private final MemberRepository memberRepository;
    private final MemberSpecHistoryRepository memberSpecHistoryRepository;
    private final ParticipationPeriodRepository participationPeriodRepository;
    private final ClanVaultRepository vaultRepository;
    private final VaultTransactionRepository transactionRepository;
    private final DistributionSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public DistributionResponseDto calculate(DistributionRequestDto request) {
        DistributionSettings settings = normalize(request);
        ParticipationResponseDto participation = getParticipationForDistribution(request);
        Map<Long, Member> memberMap = memberRepository.findByActiveTrueOrderByMemberIdAsc().stream()
                .collect(Collectors.toMap(Member::getMemberId, Function.identity()));

        List<DistributionResponseDto.ResultItemDto> baseResults = participation.getRows().stream()
                .filter(row -> memberMap.containsKey(row.getMemberId()))
                .map(row -> toBaseResult(row, memberMap.get(row.getMemberId()), settings))
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
        vault.setBalanceDiamonds(vault.getBalanceDiamonds() - totalAmount);
        vaultRepository.save(vault);

        Map<Long, Member> memberMap = memberRepository.findByActiveTrueOrderByMemberIdAsc().stream()
                .collect(Collectors.toMap(Member::getMemberId, Function.identity()));
        String memo = request.getMemo() == null || request.getMemo().isBlank()
                ? "분배금 자동 적립"
                : request.getMemo().trim();

        response.getResults().stream()
                .filter(row -> row.getFinalAmount() != null && row.getFinalAmount() > 0)
                .forEach(row -> {
                    Member target = memberMap.get(row.getMemberId());
                    if (target != null) {
                        transactionRepository.save(VaultTransaction.builder()
                                .type(VaultTransactionType.DISTRIBUTION)
                                .amountDiamonds(row.getFinalAmount())
                                .balanceAfter(vault.getBalanceDiamonds())
                                .targetMember(target)
                                .createdBy(admin)
                                .memo(memo + " · " + row.getCharacterName())
                                .claimed(false)
                                .claimedAt(null)
                                .build());
                    }
        });
        return response;
    }

    private ParticipationResponseDto getParticipationForDistribution(DistributionRequestDto request) {
        ParticipationPeriod period = resolveParticipationPeriod(request);
        if (period == null) {
            return participationService.getParticipation(null, null);
        }
        return participationService.getParticipation(period.getStartDate(), period.getEndDate());
    }

    private ParticipationPeriod resolveParticipationPeriod(DistributionRequestDto request) {
        if (request != null && request.getPeriodId() != null) {
            return participationPeriodRepository.findById(request.getPeriodId()).orElse(null);
        }
        if (request != null && request.getPeriodIndex() != null) {
            return participationPeriodRepository.findByPeriodIndex(request.getPeriodIndex()).orElse(null);
        }
        List<ParticipationPeriod> periods = participationPeriodRepository.findAllByOrderByPeriodIndexAsc();
        if (periods.isEmpty()) {
            return null;
        }
        LocalDate today = LocalDate.now();
        return periods.stream()
                .filter(period -> !today.isBefore(period.getStartDate()) && !today.isAfter(period.getEndDate()))
                .findFirst()
                .orElse(periods.get(periods.size() - 1));
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
        boolean powerEligible = powerScore >= settings.powerScoreCut();

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
                .participationRate(row.getParticipationRate())
                .finalParticipationScore(row.getFinalParticipationScore())
                .participationEligible(participationEligible)
                .powerEligible(powerEligible)
                .participationAmount(0L)
                .powerAmount(0L)
                .finalAmount(0L)
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
        results.forEach(row -> groups.computeIfAbsent(row.getClanName(), ignored -> new ArrayList<>()).add(row));
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
                .participationRate(row.getParticipationRate())
                .finalParticipationScore(row.getFinalParticipationScore())
                .participationEligible(row.getParticipationEligible())
                .powerEligible(row.getPowerEligible())
                .participationAmount(participationAmount)
                .powerAmount(powerAmount)
                .finalAmount(participationAmount + powerAmount)
                .build();
    }

    private DistributionSettings normalize(DistributionRequestDto request) {
        String mode = request.getMode() == null ? "CLAN" : request.getMode().trim().toUpperCase();
        if (!mode.equals("TOTAL")) {
            mode = "CLAN";
        }
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
                totalPowerDiamonds
        );
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
        if (raw == null || raw.isBlank()) {
            return "귀신";
        }
        String normalized = raw.trim().toLowerCase();
        if (normalized.contains("운좋")) return "운좋은";
        if (normalized.contains("로망")) return "로망";
        if (normalized.contains("z") || normalized.contains("ｚ")) return "귀신Z";
        return "귀신";
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
            long totalPowerDiamonds
    ) {
    }

    private record DistributionGroupResult(
            DistributionResponseDto.ClanSummaryDto summary,
            List<DistributionResponseDto.ResultItemDto> results
    ) {
    }
}
