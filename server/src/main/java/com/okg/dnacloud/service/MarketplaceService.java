package com.okg.dnacloud.service;

import com.okg.dnacloud.entity.PackageVersionEntity;
import com.okg.dnacloud.entity.PackageVersionEntity.PackageStatus;
import com.okg.dnacloud.model.DnaPackageInfo;
import com.okg.dnacloud.model.DnaPayout;
import com.okg.dnacloud.model.DnaPrice;
import com.okg.dnacloud.repository.PackageVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private final PackageVersionRepository packageVersionRepo;

    private static final List<DnaPackageInfo> OFFICIAL_CATALOG = buildCatalog();

    public List<DnaPackageInfo> search(String query) {
        log.info("[MarketplaceService.search] start, query={}", query);
        String q = query == null ? "" : query.toLowerCase();

        List<DnaPackageInfo> results = new ArrayList<>();

        // Official packages
        OFFICIAL_CATALOG.stream()
            .filter(p -> matchesQuery(p, q))
            .forEach(results::add);

        // Creator packages from DB
        List<PackageVersionEntity> creatorPkgs = packageVersionRepo.findByStatus(PackageStatus.published);
        creatorPkgs.stream()
            .filter(p -> matchesCreatorPackage(p, q))
            .map(this::toPackageInfo)
            .forEach(results::add);

        log.info("[MarketplaceService.search] end, resultCount={}", results.size());
        return results;
    }

    public DnaPackageInfo getById(String packageId) {
        log.info("[MarketplaceService.getById] start, packageId={}", packageId);

        // Check official catalog first
        Optional<DnaPackageInfo> official = OFFICIAL_CATALOG.stream()
            .filter(p -> p.getId().equals(packageId)).findFirst();
        if (official.isPresent()) {
            log.info("[MarketplaceService.getById] found in official catalog");
            return official.get();
        }

        // Check creator packages
        Optional<PackageVersionEntity> creator = packageVersionRepo
            .findByPackageIdAndVersion(packageId, "latest")
            .or(() -> packageVersionRepo.findByPackageIdAndVersion(packageId, "1.0.0"));

        if (creator.isEmpty()) {
            // Try to find any published version
            List<PackageVersionEntity> all = packageVersionRepo.findByStatus(PackageStatus.published);
            creator = all.stream().filter(p -> p.getPackageId().equals(packageId)).findFirst();
        }

        DnaPackageInfo result = creator.map(this::toPackageInfo).orElse(null);
        log.info("[MarketplaceService.getById] end, found={}", result != null);
        return result;
    }

    private boolean matchesQuery(DnaPackageInfo p, String q) {
        if (q.isBlank()) return true;
        return p.getId().toLowerCase().contains(q)
                || p.getName().toLowerCase().contains(q)
                || p.getDomain().toLowerCase().contains(q)
                || p.getDescription().toLowerCase().contains(q)
                || p.getCapabilities().stream().anyMatch(c -> c.toLowerCase().contains(q));
    }

    private boolean matchesCreatorPackage(PackageVersionEntity p, String q) {
        if (q.isBlank()) return true;
        return p.getPackageId().toLowerCase().contains(q)
            || p.getName().toLowerCase().contains(q)
            || p.getCategory().toLowerCase().contains(q)
            || (p.getDescription() != null && p.getDescription().toLowerCase().contains(q))
            || (p.getTags() != null && p.getTags().toLowerCase().contains(q));
    }

    private DnaPackageInfo toPackageInfo(PackageVersionEntity p) {
        return DnaPackageInfo.builder()
            .id(p.getPackageId())
            .name(p.getName())
            .version(p.getVersion())
            .domain(p.getCategory())
            .description(p.getDescription() != null ? p.getDescription() : "")
            .packageType("community-pack")
            .objective("Community-uploaded capability pack")
            .capabilities(List.of())
            .notGuaranteed(List.of("profitability", "quality"))
            .price(DnaPrice.builder()
                .amount(p.getPriceAmount())
                .currency(p.getPriceCurrency())
                .network(p.getPriceNetwork())
                .build())
            .dnaScore(p.getDnaScore())
            .certified(false)
            .build();
    }

    private static List<DnaPackageInfo> buildCatalog() {
        return List.of(
            DnaPackageInfo.builder()
                .id("trading-master-dna")
                .name("Trading Master DNA")
                .version("1.0.0")
                .domain("trading")
                .description("为 Claude Code 安装完整的交易工作流能力，包含市场分析、资金管理、风控、订单预览和复盘。不承诺盈利。")
                .packageType("official-capability-pack")
                .objective("install trading capabilities into Claude Code, not optimize profitability")
                .capabilities(List.of(
                    "market_analysis",
                    "position_management",
                    "strategy_workflow",
                    "risk_control",
                    "order_preview",
                    "live_order_tool_integration",
                    "trade_journal",
                    "post_trade_review"
                ))
                .notGuaranteed(List.of("profitability", "win_rate", "investment_advice", "risk_free_trading"))
                .price(DnaPrice.builder()
                    .amount("0.001")
                    .currency("USDT")
                    .network("eip155:196")
                    .build())
                .payout(DnaPayout.builder()
                    .address("0x0000000000000000000000000000000000000001")
                    .currency("USDT")
                    .network("eip155:196")
                    .asset(System.getenv().getOrDefault("USDT_CONTRACT_ADDRESS", ""))
                    .build())
                .dnaScore(95)
                .certified(true)
                .build()
        );
    }
}
