package com.clanmanager.clanmanager.controller;

import com.clanmanager.clanmanager.entity.*;
import com.clanmanager.clanmanager.repository.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/management")
@RequiredArgsConstructor
public class ManagementRecordController {

    private final InventoryItemRepository inventoryItemRepository;
    private final ItemBidRepository itemBidRepository;
    private final CollectionRecordRepository collectionRecordRepository;
    private final MemberRepository memberRepository;

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
}
