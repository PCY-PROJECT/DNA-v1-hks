package com.okg.dnacloud.service;

import com.okg.dnacloud.entity.PackageVersionEntity;
import com.okg.dnacloud.repository.PackageVersionRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DnaScoreService {

    private final PackageVersionRepository packageVersionRepo;

    @PostConstruct
    public void backfillScores() {
        List<PackageVersionEntity> unscored = packageVersionRepo.findByDnaScoreIsNull();
        if (unscored.isEmpty()) return;
        log.info("[DnaScoreService.backfillScores] found {} unscored packages, backfilling...", unscored.size());
        for (PackageVersionEntity pkg : unscored) {
            try {
                pkg.setDnaScore(score(pkg));
                packageVersionRepo.save(pkg);
            } catch (Exception e) {
                log.warn("[DnaScoreService.backfillScores] failed for packageId={}: {}", pkg.getPackageId(), e.getMessage());
            }
        }
        log.info("[DnaScoreService.backfillScores] backfill complete, count={}", unscored.size());
    }

    /**
     * 对包自动评分，返回 0-100 的分数。
     * 评分维度：
     * - description 非空且长度 > 30：+25
     * - description 长度 > 100：再 +15
     * - category 不是 "general"：+20
     * - payout address 非空：+15
     * - 价格在合理范围（0 < price <= 10 USDT）：+15
     * - name 长度 > 5：+10
     */
    public int score(PackageVersionEntity pkg) {
        int total = 0;

        // description 评分
        String desc = pkg.getDescription();
        if (desc != null && desc.length() > 30) {
            total += 25;
            if (desc.length() > 100) {
                total += 15;
            }
        }

        // category 不是 "general"
        String category = pkg.getCategory();
        if (category != null && !category.equalsIgnoreCase("general")) {
            total += 20;
        }

        // payout address 非空
        String payoutAddress = pkg.getPayoutAddress();
        if (payoutAddress != null && !payoutAddress.isBlank()) {
            total += 15;
        }

        // 价格合理范围：0 < price <= 10 USDT
        try {
            BigDecimal price = new BigDecimal(pkg.getPriceAmount());
            String currency = pkg.getPriceCurrency();
            if ("USDT".equalsIgnoreCase(currency)
                    && price.compareTo(BigDecimal.ZERO) > 0
                    && price.compareTo(new BigDecimal("10")) <= 0) {
                total += 15;
            }
        } catch (Exception e) {
            log.debug("[DnaScoreService.score] price parse error: {}", e.getMessage());
        }

        // name 长度 > 5
        String name = pkg.getName();
        if (name != null && name.length() > 5) {
            total += 10;
        }

        // 截断到 100
        return Math.min(total, 100);
    }
}
