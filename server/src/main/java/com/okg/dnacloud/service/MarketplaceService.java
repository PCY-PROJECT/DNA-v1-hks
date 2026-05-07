package com.okg.dnacloud.service;

import com.okg.dnacloud.model.DnaPackageInfo;
import com.okg.dnacloud.model.DnaPrice;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class MarketplaceService {

    private static final List<DnaPackageInfo> CATALOG = buildCatalog();

    public List<DnaPackageInfo> search(String query) {
        log.info("[MarketplaceService.search] start, query={}", query);
        String q = query == null ? "" : query.toLowerCase();
        List<DnaPackageInfo> results = CATALOG.stream()
                .filter(p -> matchesQuery(p, q))
                .toList();
        log.info("[MarketplaceService.search] end, resultCount={}", results.size());
        return results;
    }

    public DnaPackageInfo getById(String packageId) {
        log.info("[MarketplaceService.getById] start, packageId={}", packageId);
        DnaPackageInfo result = CATALOG.stream()
                .filter(p -> p.getId().equals(packageId))
                .findFirst()
                .orElse(null);
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
                    .currency("USDG")
                    .network("xlayer")
                    .build())
                .build()
        );
    }
}
