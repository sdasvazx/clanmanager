package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.dto.DistributionRequestDto;
import com.clanmanager.clanmanager.dto.DistributionResponseDto;
import com.clanmanager.clanmanager.service.DistributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/distributions")
@RequiredArgsConstructor
public class DistributionController {

    private final DistributionService distributionService;

    @PostMapping("/calculate")
    public DistributionResponseDto calculate(@RequestBody DistributionRequestDto request) {
        return distributionService.calculate(request);
    }

    @PostMapping("/deposit")
    public DistributionResponseDto deposit(@RequestBody DistributionRequestDto request) {
        return distributionService.depositDistributions(request);
    }

    @PostMapping("/snapshots")
    public DistributionResponseDto saveSnapshot(@RequestBody DistributionRequestDto request) {
        return distributionService.saveSnapshot(request);
    }

    @GetMapping("/snapshots")
    public List<DistributionResponseDto.SnapshotSummaryDto> getSnapshots() {
        return distributionService.getSnapshots();
    }

    @GetMapping("/snapshots/{snapshotId}")
    public DistributionResponseDto getSnapshot(@PathVariable Long snapshotId) {
        return distributionService.getSnapshot(snapshotId);
    }

    @PatchMapping("/snapshots/{snapshotId}/members/{memberId}/distributed")
    public DistributionResponseDto updateDistributed(@PathVariable Long snapshotId, @PathVariable Long memberId,
                                                      @RequestParam Long adminMemberId,
                                                      @RequestBody Map<String, Boolean> body) {
        return distributionService.updateDistributed(snapshotId, memberId, adminMemberId, Boolean.TRUE.equals(body.get("distributed")));
    }
}
