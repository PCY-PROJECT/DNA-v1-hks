package com.okg.dnacloud;

import com.okg.dnacloud.model.DnaPackageInfo;
import com.okg.dnacloud.service.MarketplaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MarketplaceServiceTest {

    @Autowired
    private MarketplaceService marketplaceService;

    @Test
    void searchReturnsAllPackagesForEmptyQuery() {
        List<DnaPackageInfo> results = marketplaceService.search("");
        assertFalse(results.isEmpty());
    }

    @Test
    void searchMatchesByDomain() {
        List<DnaPackageInfo> results = marketplaceService.search("trading");
        assertTrue(results.stream().anyMatch(p -> p.getId().equals("trading-master-dna")));
    }

    @Test
    void searchMatchesByCapability() {
        List<DnaPackageInfo> results = marketplaceService.search("market_analysis");
        assertFalse(results.isEmpty());
    }

    @Test
    void getByIdReturnsTradingMasterDna() {
        DnaPackageInfo pkg = marketplaceService.getById("trading-master-dna");
        assertNotNull(pkg);
        assertEquals("Trading Master DNA", pkg.getName());
        assertEquals("1.0.0", pkg.getVersion());
        assertEquals("official-capability-pack", pkg.getPackageType());
        assertFalse(pkg.getCapabilities().isEmpty());
        assertNotNull(pkg.getPrice());
        assertEquals("USDT", pkg.getPrice().getCurrency());
    }

    @Test
    void getByIdReturnsNullForUnknownPackage() {
        assertNull(marketplaceService.getById("unknown-package"));
    }
}
