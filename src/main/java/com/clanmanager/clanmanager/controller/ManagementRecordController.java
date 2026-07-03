package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.entity.*;
import com.clanmanager.clanmanager.repository.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/management")
@RequiredArgsConstructor
public class ManagementRecordController {

    private final InventoryItemRepository inventoryItemRepository;
    private final ItemBidRepository itemBidRepository;
    private final CollectionRecordRepository collectionRecordRepository;
    private final CollectionItemRepository collectionItemRepository;
    private final CollectionStatusRepository collectionStatusRepository;
    private final CollectionHistoryRepository collectionHistoryRepository;
    private final MemberRepository memberRepository;

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
    public InventoryItem createInventory(@RequestBody InventoryRequest request) {
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

    @GetMapping("/bids")
    public List<ItemBid> getBids() {
        return itemBidRepository.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/bids")
    public ItemBid createBid(@RequestBody BidRequest request) {
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
    public CollectionItemDto createCollectionItem(@RequestBody CollectionItemRequest request) {
        Member actor = validateMember(request.getActorMemberId());
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
    public CollectionItemDto updateCollectionItem(@PathVariable Long itemId, @RequestBody CollectionItemRequest request) {
        Member actor = validateMember(request.getActorMemberId());
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
        Member actor = validateMember(actorMemberId);
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
    public CollectionStatusDto updateCollectionStatus(@RequestBody CollectionStatusRequest request) {
        Member actor = validateMember(request.getActorMemberId());
        Member target = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 클랜원입니다."));
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

    @PostMapping("/collections")
    public CollectionRecord createCollection(@RequestBody CollectionRequest request) {
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

    private void validateAdmin(Long adminMemberId) {
        Member admin = memberRepository.findById(adminMemberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 클랜원입니다."));
        if (admin.getRole() != MemberRole.ADMIN) {
            throw new SecurityException("운영자만 관리 기록을 변경할 수 있습니다.");
        }
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
        private String itemName;
        private Integer quantity;
        private String location;
        private String memo;
        private Long adminMemberId;
    }

    @Getter
    @Setter
    public static class BidRequest {
        private String itemName;
        private String bidder;
        private Long bidDiamonds;
        private String memo;
        private Long adminMemberId;
    }

    @Getter
    @Setter
    public static class CollectionRequest {
        private String characterName;
        private String itemName;
        private String state;
        private String memo;
        private Long adminMemberId;
    }

    @Getter
    @Setter
    public static class CollectionItemRequest {
        private String itemName;
        private Long actorMemberId;
    }

    @Getter
    @Setter
    public static class CollectionStatusRequest {
        private Long memberId;
        private Long itemId;
        private String state;
        private String memo;
        private Long actorMemberId;
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
