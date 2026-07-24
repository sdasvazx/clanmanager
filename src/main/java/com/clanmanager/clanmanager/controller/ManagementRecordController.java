package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.entity.*;
import com.clanmanager.clanmanager.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/management")
@RequiredArgsConstructor
public class ManagementRecordController {

    private final InventoryItemRepository inventoryItemRepository;
    private final AllItemStockRepository allItemStockRepository;
    private final ItemBidRepository itemBidRepository;
    private final CollectionRecordRepository collectionRecordRepository;
    private final CollectionItemRepository collectionItemRepository;
    private final CollectionStatusRepository collectionStatusRepository;
    private final CollectionHistoryRepository collectionHistoryRepository;
    private final ItemRequestRepository itemRequestRepository;
    private final MemberRepository memberRepository;

    private static final List<String> ALL_ITEM_CLANS = List.of("귀신", "운좋은", "귀신Z", "로망");

    private static final List<DefaultAllItem> DEFAULT_ALL_ITEMS = List.of(
            new DefaultAllItem("T2", "무기", "오브"),
            new DefaultAllItem("T2", "무기", "낫"),
            new DefaultAllItem("T2", "무기", "총"),
            new DefaultAllItem("T2", "무기", "검"),
            new DefaultAllItem("T2", "무기", "톤파"),
            new DefaultAllItem("T2", "방어구", "투구"),
            new DefaultAllItem("T2", "방어구", "갑옷"),
            new DefaultAllItem("T2", "방어구", "장갑"),
            new DefaultAllItem("T2", "방어구", "신발"),
            new DefaultAllItem("T2", "장신구", "벨트"),
            new DefaultAllItem("T2", "장신구", "목걸이"),
            new DefaultAllItem("T2", "장신구", "귀걸이"),
            new DefaultAllItem("T2", "장신구", "팔찌"),
            new DefaultAllItem("T2", "장신구", "반지"),
            new DefaultAllItem("T2", "장신구", "문장"),
            new DefaultAllItem("T3", "무기", "오브"),
            new DefaultAllItem("T3", "무기", "낫"),
            new DefaultAllItem("T3", "무기", "총"),
            new DefaultAllItem("T3", "무기", "검"),
            new DefaultAllItem("T3", "무기", "톤파"),
            new DefaultAllItem("T3", "방어구", "투구"),
            new DefaultAllItem("T3", "방어구", "갑옷"),
            new DefaultAllItem("T3", "방어구", "장갑"),
            new DefaultAllItem("T3", "방어구", "신발"),
            new DefaultAllItem("T3", "장신구", "벨트"),
            new DefaultAllItem("T3", "장신구", "목걸이"),
            new DefaultAllItem("T3", "장신구", "귀걸이"),
            new DefaultAllItem("T3", "장신구", "팔찌"),
            new DefaultAllItem("T3", "장신구", "반지"),
            new DefaultAllItem("T3", "장신구", "문장")
    );

    private static final List<String> DEFAULT_COLLECTION_ITEMS = List.of(
            "행동불가저항[3000]",
            "피해저항력강화",
            "적중력강화",
            "pvp공격강화2",
            "pvp방어강화1",
            "급소공격3",
            "냉혈의규율",
            "일반공격강화2[1500]",
            "물약회복률2[1100]",
            "회피력증가1",
            "pvp방어강화2",
            "위력강화3",
            "무기손질",
            "영웅문장"
    );

    @GetMapping("/inventory")
    public List<InventoryItem> getInventory() {
        return inventoryItemRepository.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/inventory")
    public InventoryItem createInventory(@Valid @RequestBody InventoryRequest request) {
        validateAdmin(request.getAdminMemberId());
        return inventoryItemRepository.save(InventoryItem.builder()
                .itemName(request.getItemName())
                .quantity(request.getQuantity())
                .location(request.getLocation())
                .memo(request.getMemo())
                .build());
    }

    @DeleteMapping("/inventory/{itemId}")
    public Map<String, Object> deleteInventory(@PathVariable Long itemId, @RequestParam Long adminMemberId) {
        validateAdmin(adminMemberId);
        inventoryItemRepository.deleteById(itemId);
        return Map.of("message", "재고 기록 삭제 완료", "itemId", itemId);
    }

    @GetMapping("/all-items")
    public List<AllItemStock> getAllItems(@RequestParam(required = false) String clanName) {
        if (clanName == null || clanName.isBlank() || "총합".equals(clanName)) {
            ALL_ITEM_CLANS.forEach(this::seedDefaultAllItems);
            return aggregateAllItemStocks();
        }
        String targetClanName = requireAllItemClanName(clanName);
        seedDefaultAllItems(targetClanName);
        return allItemStockRepository.findAllByClanNameOrderByDisplayOrderAscAllItemStockIdAsc(targetClanName);
    }

    @PutMapping("/all-items")
    @Transactional
    public List<AllItemStock> saveAllItems(@Valid @RequestBody AllItemStockSaveRequest request) {
        validateAdmin(request.getAdminMemberId());
        String targetClanName = requireAllItemClanName(request.getClanName());
        allItemStockRepository.deleteAllByClanName(targetClanName);
        List<AllItemStock> rows = new ArrayList<>();
        int order = 1;
        for (AllItemStockRow row : request.getRows()) {
            rows.add(AllItemStock.builder()
                    .clanName(targetClanName)
                    .tierName(cleanRequired(row.getTierName(), "티어를 입력해 주세요."))
                    .categoryName(cleanRequired(row.getCategoryName(), "분류를 입력해 주세요."))
                    .itemName(cleanRequired(row.getItemName(), "아이템명을 입력해 주세요."))
                    .stockQuantity(number(row.getStockQuantity()))
                    .paidQuantity(number(row.getPaidQuantity()))
                    .displayOrder(order++)
                    .build());
        }
        allItemStockRepository.saveAll(rows);
        return allItemStockRepository.findAllByClanNameOrderByDisplayOrderAscAllItemStockIdAsc(targetClanName);
    }

    @GetMapping("/bids")
    public List<ItemBid> getBids() {
        return itemBidRepository.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/bids")
    public ItemBid createBid(@Valid @RequestBody BidRequest request) {
        validateAdmin(request.getAdminMemberId());
        return itemBidRepository.save(ItemBid.builder()
                .itemName(request.getItemName())
                .bidder(request.getBidder())
                .bidDiamonds(request.getBidDiamonds())
                .memo(request.getMemo())
                .build());
    }

    @DeleteMapping("/bids/{bidId}")
    public Map<String, Object> deleteBid(@PathVariable Long bidId, @RequestParam Long adminMemberId) {
        validateAdmin(adminMemberId);
        itemBidRepository.deleteById(bidId);
        return Map.of("message", "입찰 기록 삭제 완료", "bidId", bidId);
    }

    @GetMapping("/item-requests")
    public List<ItemRequestDto> getItemRequests() {
        return itemRequestRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(ItemRequestDto::from)
                .toList();
    }

    @PostMapping("/item-requests")
    public ItemRequestDto createItemRequest(@Valid @RequestBody ItemRequestCreateRequest request) {
        Member requester = validateMember(request.getRequesterMemberId());
        String itemName = cleanRequired(request.getItemName(), "신청할 아이템명을 입력해 주세요.");
        ItemRequest saved = itemRequestRepository.save(ItemRequest.builder()
                .requester(requester)
                .requesterName(requester.getCharacterName())
                .itemName(itemName)
                .memo(clean(request.getMemo()))
                .status("접수")
                .build());
        return ItemRequestDto.from(saved);
    }

    @PatchMapping("/item-requests/{requestId}")
    public ItemRequestDto processItemRequest(@PathVariable Long requestId, @Valid @RequestBody ItemRequestProcessRequest request) {
        Member actor = validateAdmin(request.getActorMemberId());
        ItemRequest itemRequest = itemRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이템 신청입니다."));

        String status = cleanRequired(request.getStatus(), "처리 상태를 선택해 주세요.");
        if (!List.of("접수", "지급완료", "반려").contains(status)) {
            throw new IllegalArgumentException("처리 상태는 접수/지급완료/반려 중 하나여야 합니다.");
        }
        if (!itemRequest.getRequester().getMemberId().equals(actor.getMemberId()) && actor.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 다른 클랜원의 아이템 신청을 처리할 수 있습니다.");
        }
        if (!"접수".equals(status) && actor.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("아이템 지급/반려 처리는 운영자만 할 수 있습니다.");
        }

        itemRequest.setStatus(status);
        itemRequest.setProcessedMemo(clean(request.getProcessedMemo()));
        itemRequest.setProcessedBy(actor);
        itemRequest.setProcessedByName(actor.getCharacterName());
        itemRequest.setProcessedAt(java.time.LocalDateTime.now());
        return ItemRequestDto.from(itemRequestRepository.save(itemRequest));
    }

    @GetMapping("/collections")
    public List<CollectionRecord> getCollections() {
        return collectionRecordRepository.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/collection-dashboard")
    public CollectionDashboardResponse getCollectionDashboard() {
        seedDefaultCollectionItems();
        List<CollectionItemDto> items = collectionItemRepository.findActiveItems().stream()
                .map(CollectionItemDto::from)
                .toList();
        List<MemberCollectionDto> members = memberRepository.findByActiveTrueOrderByMemberIdAsc().stream()
                .sorted(Comparator
                        .comparing((Member member) -> member.getGuildName() == null ? "" : member.getGuildName())
                        .thenComparing(Member::getMemberId))
                .map(MemberCollectionDto::from)
                .toList();
        List<CollectionStatusDto> statuses = collectionStatusRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(CollectionStatusDto::from)
                .toList();
        List<CollectionHistoryDto> histories = collectionHistoryRepository.findTop80ByOrderByCreatedAtDesc().stream()
                .map(CollectionHistoryDto::from)
                .toList();
        return new CollectionDashboardResponse(items, members, statuses, histories);
    }

    @PostMapping("/collection-items")
    public CollectionItemDto createCollectionItem(@Valid @RequestBody CollectionItemRequest request) {
        Member actor = validateAdmin(request.getActorMemberId());
        String itemName = cleanRequired(request.getItemName(), "항목명을 입력해 주세요.");
        if (collectionItemRepository.existsByItemName(itemName)) {
            throw new IllegalArgumentException("이미 등록된 컬렉템 항목입니다.");
        }
        CollectionItem item = collectionItemRepository.save(CollectionItem.builder()
                .itemName(itemName)
                .sortOrder((int) collectionItemRepository.countByActiveTrue() + 1)
                .active(true)
                .build());
        collectionHistoryRepository.save(CollectionHistory.builder()
                .memberId(0L)
                .characterName("전체")
                .collectionItemId(item.getCollectionItemId())
                .itemName(item.getItemName())
                .action("항목추가")
                .nextState("등록")
                .editedByMemberId(actor.getMemberId())
                .editedByName(actor.getCharacterName())
                .build());
        return CollectionItemDto.from(item);
    }

    @PatchMapping("/collection-items/{itemId}")
    public CollectionItemDto updateCollectionItem(@PathVariable Long itemId, @Valid @RequestBody CollectionItemRequest request) {
        Member actor = validateAdmin(request.getActorMemberId());
        CollectionItem item = collectionItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컬렉템 항목입니다."));
        String oldName = item.getItemName();
        String itemName = cleanRequired(request.getItemName(), "항목명을 입력해 주세요.");
        collectionItemRepository.findByItemName(itemName)
                .filter(existing -> !existing.getCollectionItemId().equals(itemId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("이미 등록된 컬렉템 항목입니다.");
                });
        item.setItemName(itemName);
        CollectionItem saved = collectionItemRepository.save(item);
        collectionHistoryRepository.save(CollectionHistory.builder()
                .memberId(0L)
                .characterName("전체")
                .collectionItemId(saved.getCollectionItemId())
                .itemName(saved.getItemName())
                .action("항목수정")
                .previousState(oldName)
                .nextState(saved.getItemName())
                .editedByMemberId(actor.getMemberId())
                .editedByName(actor.getCharacterName())
                .build());
        return CollectionItemDto.from(saved);
    }

    @DeleteMapping("/collection-items/{itemId}")
    public Map<String, Object> deleteCollectionItem(@PathVariable Long itemId, @RequestParam Long actorMemberId) {
        Member actor = validateAdmin(actorMemberId);
        CollectionItem item = collectionItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컬렉템 항목입니다."));
        item.setActive(false);
        collectionItemRepository.save(item);
        collectionHistoryRepository.save(CollectionHistory.builder()
                .memberId(0L)
                .characterName("전체")
                .collectionItemId(item.getCollectionItemId())
                .itemName(item.getItemName())
                .action("항목삭제")
                .previousState("사용")
                .nextState("숨김")
                .editedByMemberId(actor.getMemberId())
                .editedByName(actor.getCharacterName())
                .build());
        return Map.of("message", "컬렉템 항목 삭제 완료", "itemId", itemId);
    }

    @PatchMapping("/collection-statuses")
    public CollectionStatusDto updateCollectionStatus(@Valid @RequestBody CollectionStatusRequest request) {
        Member actor = validateMember(request.getActorMemberId());
        Member target = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 클랜원입니다."));
        if (actor.getRole() != MemberRole.ADMIN && !actor.getMemberId().equals(target.getMemberId())) {
            throw new SecurityException("일반 클랜원은 본인의 컬렉템 지급 상태만 수정할 수 있습니다.");
        }
        CollectionItem item = collectionItemRepository.findById(request.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컬렉템 항목입니다."));
        String nextState = cleanRequired(request.getState(), "상태를 선택해 주세요.");
        CollectionStatus status = collectionStatusRepository.findByMemberAndItem(target, item)
                .orElseGet(() -> CollectionStatus.builder()
                        .member(target)
                        .item(item)
                        .state("미완료")
                        .build());
        String previousState = status.getCollectionStatusId() == null ? "미완료" : status.getState();
        if (actor.getRole() != MemberRole.ADMIN && Boolean.TRUE.equals(status.getLocked())) {
            throw new SecurityException("운영자가 잠근 항목은 수정할 수 없습니다.");
        }
        status.setState(nextState);
        status.setMemo(request.getMemo());
        status.setUpdatedByMemberId(actor.getMemberId());
        status.setUpdatedByName(actor.getCharacterName());
        CollectionStatus saved = collectionStatusRepository.save(status);
        collectionHistoryRepository.save(CollectionHistory.builder()
                .memberId(target.getMemberId())
                .characterName(target.getCharacterName())
                .collectionItemId(item.getCollectionItemId())
                .itemName(item.getItemName())
                .action("상태변경")
                .previousState(previousState)
                .nextState(nextState)
                .memo(request.getMemo())
                .editedByMemberId(actor.getMemberId())
                .editedByName(actor.getCharacterName())
                .build());
        return CollectionStatusDto.from(saved);
    }

    @PatchMapping("/collection-statuses/self")
    public CollectionStatusDto updateOwnCollectionStatus(@Valid @RequestBody CollectionStatusRequest request) {
        return updateCollectionStatus(request);
    }

    @PatchMapping("/collection-statuses/lock")
    public CollectionStatusDto updateCollectionStatusLock(@Valid @RequestBody CollectionStatusLockRequest request) {
        Member actor = validateAdmin(request.getActorMemberId());
        Member target = validateMember(request.getMemberId());
        CollectionItem item = collectionItemRepository.findById(request.getItemId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 컬렉템 항목입니다."));
        CollectionStatus status = collectionStatusRepository.findByMemberAndItem(target, item)
                .orElseGet(() -> CollectionStatus.builder()
                        .member(target)
                        .item(item)
                        .state("\uBBF8\uC644\uB8CC")
                        .locked(false)
                        .build());
        boolean previousLocked = Boolean.TRUE.equals(status.getLocked());
        status.setLocked(request.getLocked());
        status.setUpdatedByMemberId(actor.getMemberId());
        status.setUpdatedByName(actor.getCharacterName());
        CollectionStatus saved = collectionStatusRepository.save(status);
        collectionHistoryRepository.save(CollectionHistory.builder()
                .memberId(target.getMemberId())
                .characterName(target.getCharacterName())
                .collectionItemId(item.getCollectionItemId())
                .itemName(item.getItemName())
                .action("\uC7A0\uAE08\uBCC0\uACBD")
                .previousState(previousLocked ? "\uC7A0\uAE08" : "\uC218\uC815\uAC00\uB2A5")
                .nextState(request.getLocked() ? "\uC7A0\uAE08" : "\uC218\uC815\uAC00\uB2A5")
                .editedByMemberId(actor.getMemberId())
                .editedByName(actor.getCharacterName())
                .build());
        return CollectionStatusDto.from(saved);
    }

    @PatchMapping("/collection-statuses/lock-member")
    @Transactional
    public List<CollectionStatusDto> updateMemberCollectionStatusLocks(@Valid @RequestBody CollectionMemberLockRequest request) {
        Member actor = validateAdmin(request.getActorMemberId());
        Member target = validateMember(request.getMemberId());
        List<CollectionStatusDto> updated = new ArrayList<>();
        for (CollectionItem item : collectionItemRepository.findActiveItems()) {
            CollectionStatus status = collectionStatusRepository.findByMemberAndItem(target, item)
                    .orElseGet(() -> CollectionStatus.builder()
                            .member(target)
                            .item(item)
                            .state("\uBBF8\uC644\uB8CC")
                            .locked(false)
                            .build());
            boolean previousLocked = Boolean.TRUE.equals(status.getLocked());
            if (previousLocked == request.getLocked()) {
                updated.add(CollectionStatusDto.from(status));
                continue;
            }
            status.setLocked(request.getLocked());
            status.setUpdatedByMemberId(actor.getMemberId());
            status.setUpdatedByName(actor.getCharacterName());
            CollectionStatus saved = collectionStatusRepository.save(status);
            collectionHistoryRepository.save(CollectionHistory.builder()
                    .memberId(target.getMemberId())
                    .characterName(target.getCharacterName())
                    .collectionItemId(item.getCollectionItemId())
                    .itemName(item.getItemName())
                    .action("\uC77C\uAD04\uC7A0\uAE08\uBCC0\uACBD")
                    .previousState(previousLocked ? "\uC7A0\uAE08" : "\uC218\uC815\uAC00\uB2A5")
                    .nextState(request.getLocked() ? "\uC7A0\uAE08" : "\uC218\uC815\uAC00\uB2A5")
                    .editedByMemberId(actor.getMemberId())
                    .editedByName(actor.getCharacterName())
                    .build());
            updated.add(CollectionStatusDto.from(saved));
        }
        return updated;
    }

    @PostMapping("/collections")
    public CollectionRecord createCollection(@Valid @RequestBody CollectionRequest request) {
        validateAdmin(request.getAdminMemberId());
        return collectionRecordRepository.save(CollectionRecord.builder()
                .characterName(request.getCharacterName())
                .itemName(request.getItemName())
                .state(request.getState())
                .memo(request.getMemo())
                .build());
    }

    @DeleteMapping("/collections/{recordId}")
    public Map<String, Object> deleteCollection(@PathVariable Long recordId, @RequestParam Long adminMemberId) {
        validateAdmin(adminMemberId);
        collectionRecordRepository.deleteById(recordId);
        return Map.of("message", "컬렉템 기록 삭제 완료", "recordId", recordId);
    }

    private Member validateAdmin(Long adminMemberId) {
        if (adminMemberId == null) {
            throw new IllegalArgumentException("운영자 확인 정보가 필요합니다.");
        }
        Member admin = memberRepository.findById(adminMemberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 클랜원입니다."));
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 관리 기록을 변경할 수 있습니다.");
        }
        return admin;
    }

    private Member validateMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 클랜원입니다."));
    }

    private String cleanRequired(String value, String message) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return cleaned;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private int number(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(value, 0);
    }

    private void seedDefaultAllItems(String clanName) {
        if (allItemStockRepository.countByClanName(clanName) > 0) {
            return;
        }
        List<AllItemStock> defaults = new ArrayList<>();
        for (int index = 0; index < DEFAULT_ALL_ITEMS.size(); index += 1) {
            DefaultAllItem item = DEFAULT_ALL_ITEMS.get(index);
            defaults.add(AllItemStock.builder()
                    .clanName(clanName)
                    .tierName(item.tierName())
                    .categoryName(item.categoryName())
                    .itemName(item.itemName())
                    .stockQuantity(0)
                    .paidQuantity(0)
                    .displayOrder(index + 1)
                    .build());
        }
        allItemStockRepository.saveAll(defaults);
    }

    private List<AllItemStock> aggregateAllItemStocks() {
        Map<String, AllItemStock> aggregateMap = new LinkedHashMap<>();
        for (String clanName : ALL_ITEM_CLANS) {
            List<AllItemStock> rows = allItemStockRepository.findAllByClanNameOrderByDisplayOrderAscAllItemStockIdAsc(clanName);
            for (AllItemStock row : rows) {
                String key = row.getTierName() + "\u0000" + row.getCategoryName() + "\u0000" + row.getItemName();
                AllItemStock aggregate = aggregateMap.computeIfAbsent(key, ignored -> AllItemStock.builder()
                        .clanName("총합")
                        .tierName(row.getTierName())
                        .categoryName(row.getCategoryName())
                        .itemName(row.getItemName())
                        .stockQuantity(0)
                        .paidQuantity(0)
                        .displayOrder(row.getDisplayOrder())
                        .build());
                aggregate.setStockQuantity(number(aggregate.getStockQuantity()) + number(row.getStockQuantity()));
                aggregate.setPaidQuantity(number(aggregate.getPaidQuantity()) + number(row.getPaidQuantity()));
                aggregate.setDisplayOrder(Math.min(number(aggregate.getDisplayOrder()), number(row.getDisplayOrder())));
            }
        }
        return aggregateMap.values().stream()
                .sorted(Comparator
                        .comparing(AllItemStock::getDisplayOrder)
                        .thenComparing(AllItemStock::getTierName)
                        .thenComparing(AllItemStock::getCategoryName)
                        .thenComparing(AllItemStock::getItemName))
                .toList();
    }

    private String requireAllItemClanName(String clanName) {
        String cleaned = cleanRequired(clanName, "클랜을 선택해 주세요.");
        if (!ALL_ITEM_CLANS.contains(cleaned)) {
            throw new IllegalArgumentException("전체아이템은 귀신/운좋은/귀신Z/로망 클랜별 탭에서만 저장할 수 있습니다.");
        }
        return cleaned;
    }

    private void seedDefaultCollectionItems() {
        if (collectionItemRepository.countByActiveTrue() > 0) {
            return;
        }
        List<CollectionItem> defaults = new ArrayList<>();
        for (int index = 0; index < DEFAULT_COLLECTION_ITEMS.size(); index += 1) {
            String itemName = DEFAULT_COLLECTION_ITEMS.get(index);
            if (!collectionItemRepository.existsByItemName(itemName)) {
                defaults.add(CollectionItem.builder()
                        .itemName(itemName)
                        .sortOrder(index + 1)
                        .active(true)
                        .build());
            }
        }
        collectionItemRepository.saveAll(defaults);
    }

    @Getter
    @Setter
    public static class InventoryRequest {
        @NotBlank(message = "아이템명을 입력해 주세요.")
        @Size(max = 100, message = "아이템명은 100자 이하로 입력해 주세요.")
        private String itemName;

        @Min(value = 0, message = "수량은 0 이상으로 입력해 주세요.")
        private Integer quantity;

        @Size(max = 100, message = "위치는 100자 이하로 입력해 주세요.")
        private String location;

        @Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
        private String memo;

        @NotNull(message = "운영자 정보가 필요합니다.")
        private Long adminMemberId;
    }

    @Getter
    @Setter
    public static class AllItemStockSaveRequest {
        @NotNull(message = "운영자 정보가 필요합니다.")
        private Long adminMemberId;

        @NotBlank(message = "클랜을 선택해 주세요.")
        @Size(max = 20, message = "클랜명은 20자 이하로 입력해 주세요.")
        private String clanName;

        @NotNull(message = "저장할 아이템 목록이 필요합니다.")
        private List<AllItemStockRow> rows = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class AllItemStockRow {
        @NotBlank(message = "티어를 입력해 주세요.")
        @Size(max = 20, message = "티어는 20자 이하로 입력해 주세요.")
        private String tierName;

        @NotBlank(message = "분류를 입력해 주세요.")
        @Size(max = 30, message = "분류는 30자 이하로 입력해 주세요.")
        private String categoryName;

        @NotBlank(message = "아이템명을 입력해 주세요.")
        @Size(max = 50, message = "아이템명은 50자 이하로 입력해 주세요.")
        private String itemName;

        @Min(value = 0, message = "재고는 0 이상으로 입력해 주세요.")
        private Integer stockQuantity;

        @Min(value = 0, message = "지급 수량은 0 이상으로 입력해 주세요.")
        private Integer paidQuantity;
    }

    private record DefaultAllItem(String tierName, String categoryName, String itemName) {
    }

    @Getter
    @Setter
    public static class BidRequest {
        @NotBlank(message = "아이템명을 입력해 주세요.")
        @Size(max = 100, message = "아이템명은 100자 이하로 입력해 주세요.")
        private String itemName;

        @NotBlank(message = "입찰자 이름을 입력해 주세요.")
        @Size(max = 50, message = "입찰자 이름은 50자 이하로 입력해 주세요.")
        private String bidder;

        @Min(value = 0, message = "입찰 다이아는 0 이상으로 입력해 주세요.")
        private Long bidDiamonds;

        @Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
        private String memo;

        @NotNull(message = "운영자 정보가 필요합니다.")
        private Long adminMemberId;
    }

    @Getter
    @Setter
    public static class ItemRequestCreateRequest {
        @NotNull(message = "신청자 정보가 필요합니다.")
        private Long requesterMemberId;

        @NotBlank(message = "신청 아이템명을 입력해 주세요.")
        @Size(max = 100, message = "아이템명은 100자 이하로 입력해 주세요.")
        private String itemName;

        @Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
        private String memo;
    }

    @Getter
    @Setter
    public static class ItemRequestProcessRequest {
        @NotNull(message = "처리자 정보가 필요합니다.")
        private Long actorMemberId;

        @NotBlank(message = "처리 상태를 선택해 주세요.")
        @Size(max = 20, message = "처리 상태는 20자 이하로 입력해 주세요.")
        private String status;

        @Size(max = 500, message = "처리 메모는 500자 이하로 입력해 주세요.")
        private String processedMemo;
    }

    @Getter
    @Setter
    public static class CollectionRequest {
        @NotBlank(message = "캐릭터 이름을 입력해 주세요.")
        @Size(max = 50, message = "캐릭터 이름은 50자 이하로 입력해 주세요.")
        private String characterName;

        @NotBlank(message = "컬렉템명을 입력해 주세요.")
        @Size(max = 100, message = "컬렉템명은 100자 이하로 입력해 주세요.")
        private String itemName;

        @NotBlank(message = "상태를 입력해 주세요.")
        @Size(max = 20, message = "상태는 20자 이하로 입력해 주세요.")
        private String state;

        @Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
        private String memo;

        @NotNull(message = "운영자 정보가 필요합니다.")
        private Long adminMemberId;
    }

    @Getter
    @Setter
    public static class CollectionItemRequest {
        @NotBlank(message = "컬렉템명을 입력해 주세요.")
        @Size(max = 100, message = "컬렉템명은 100자 이하로 입력해 주세요.")
        private String itemName;

        @NotNull(message = "수정자 정보가 필요합니다.")
        private Long actorMemberId;
    }

    @Getter
    @Setter
    public static class CollectionStatusRequest {
        @NotNull(message = "클랜원을 선택해 주세요.")
        private Long memberId;

        @NotNull(message = "컬렉템을 선택해 주세요.")
        private Long itemId;

        @NotBlank(message = "상태를 선택해 주세요.")
        @Size(max = 20, message = "상태는 20자 이하로 입력해 주세요.")
        private String state;

        @Size(max = 500, message = "메모는 500자 이하로 입력해 주세요.")
        private String memo;

        @NotNull(message = "수정자 정보가 필요합니다.")
        private Long actorMemberId;
    }

    @Getter
    @Setter
    public static class CollectionStatusLockRequest {
        @NotNull(message = "클랜원을 선택해 주세요.")
        private Long memberId;

        @NotNull(message = "컬렉템을 선택해 주세요.")
        private Long itemId;

        @NotNull(message = "잠금 상태를 선택해 주세요.")
        private Boolean locked;

        @NotNull(message = "운영자 정보가 필요합니다.")
        private Long actorMemberId;
    }

    @Getter
    @Setter
    public static class CollectionMemberLockRequest {
        @NotNull(message = "클랜원을 선택해 주세요.")
        private Long memberId;

        @NotNull(message = "잠금 상태를 선택해 주세요.")
        private Boolean locked;

        @NotNull(message = "운영자 정보가 필요합니다.")
        private Long actorMemberId;
    }

    public record ItemRequestDto(
            Long requestId,
            Long requesterMemberId,
            String requesterName,
            String itemName,
            String memo,
            String status,
            Long processedByMemberId,
            String processedByName,
            String processedMemo,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime processedAt
    ) {
        public static ItemRequestDto from(ItemRequest itemRequest) {
            return new ItemRequestDto(
                    itemRequest.getItemRequestId(),
                    itemRequest.getRequester() == null ? null : itemRequest.getRequester().getMemberId(),
                    itemRequest.getRequesterName(),
                    itemRequest.getItemName(),
                    itemRequest.getMemo(),
                    itemRequest.getStatus(),
                    itemRequest.getProcessedBy() == null ? null : itemRequest.getProcessedBy().getMemberId(),
                    itemRequest.getProcessedByName(),
                    itemRequest.getProcessedMemo(),
                    itemRequest.getCreatedAt(),
                    itemRequest.getProcessedAt()
            );
        }
    }

    public record CollectionDashboardResponse(
            List<CollectionItemDto> items,
            List<MemberCollectionDto> members,
            List<CollectionStatusDto> statuses,
            List<CollectionHistoryDto> histories
    ) {
    }

    public record CollectionItemDto(Long itemId, String itemName, Integer sortOrder) {
        public static CollectionItemDto from(CollectionItem item) {
            return new CollectionItemDto(item.getCollectionItemId(), item.getItemName(), item.getSortOrder());
        }
    }

    public record MemberCollectionDto(Long memberId, String characterName, String guildName, String characterClass, Integer combatPower) {
        public static MemberCollectionDto from(Member member) {
            return new MemberCollectionDto(
                    member.getMemberId(),
                    member.getCharacterName(),
                    member.getGuildName(),
                    member.getCharacterClass(),
                    member.getCombatPower()
            );
        }
    }

    public record CollectionStatusDto(
            Long statusId,
            Long memberId,
            Long itemId,
            String state,
            String memo,
            Boolean locked,
            Long updatedByMemberId,
            String updatedByName,
            java.time.LocalDateTime updatedAt
    ) {
        public static CollectionStatusDto from(CollectionStatus status) {
            return new CollectionStatusDto(
                    status.getCollectionStatusId(),
                    status.getMember().getMemberId(),
                    status.getItem().getCollectionItemId(),
                    status.getState(),
                    status.getMemo(),
                    Boolean.TRUE.equals(status.getLocked()),
                    status.getUpdatedByMemberId(),
                    status.getUpdatedByName(),
                    status.getUpdatedAt()
            );
        }
    }

    public record CollectionHistoryDto(
            Long historyId,
            Long memberId,
            String characterName,
            Long itemId,
            String itemName,
            String action,
            String previousState,
            String nextState,
            String memo,
            Long editedByMemberId,
            String editedByName,
            java.time.LocalDateTime createdAt
    ) {
        public static CollectionHistoryDto from(CollectionHistory history) {
            return new CollectionHistoryDto(
                    history.getCollectionHistoryId(),
                    history.getMemberId(),
                    history.getCharacterName(),
                    history.getCollectionItemId(),
                    history.getItemName(),
                    history.getAction(),
                    history.getPreviousState(),
                    history.getNextState(),
                    history.getMemo(),
                    history.getEditedByMemberId(),
                    history.getEditedByName(),
                    history.getCreatedAt()
            );
        }
    }
}
