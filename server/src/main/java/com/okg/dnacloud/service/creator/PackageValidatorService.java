package com.okg.dnacloud.service.creator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.okg.dnacloud.model.ValidationReport;
import com.okg.dnacloud.model.ValidationIssue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PackageValidatorService {

    private static final long MAX_PACKAGE_SIZE = 50L * 1024 * 1024; // 50MB
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".md", ".json", ".yaml", ".yml", ".txt", ".png", ".svg");
    private static final Set<String> ALLOWED_PACKAGE_TYPES = Set.of("official-capability-pack", "community-pack", "personal-pack");
    private static final Set<String> SUPPORTED_NETWORKS = Set.of("eip155:196", "xlayer", "eip155:5000", "mantle");
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USDG", "USDT", "USDC");

    private static final List<Pattern> SECRET_PATTERNS = List.of(
        Pattern.compile("(?i)private[_\\s]?key\\s*[:=]\\s*[\"']?[a-fA-F0-9]{64}"),
        Pattern.compile("(?i)(mnemonic|seed[_\\s]?phrase)"),
        Pattern.compile("sk[-_][a-zA-Z0-9]{20,}"),
        Pattern.compile("eyJ[a-zA-Z0-9_-]{20,}\\.[a-zA-Z0-9_-]{20,}\\.")
    );

    private static final List<Pattern> DANGER_PATTERNS = List.of(
        Pattern.compile("rm\\s+-rf\\s+/"),
        Pattern.compile("curl[^|]+\\|\\s*(ba)?sh"),
        Pattern.compile("wget[^|]+\\|\\s*(ba)?sh"),
        Pattern.compile("chmod\\s+777"),
        Pattern.compile("~/(\\.ssh|\\.aws|\\.config|\\.kube|\\.gnupg)"),
        Pattern.compile("(?i)(paste|enter|provide|share)\\s+(your\\s+)?(private\\s+key|seed\\s+phrase|mnemonic|secret)")
    );

    private final ObjectMapper objectMapper;

    public ValidationReport validateZip(File zipFile) {
        log.info("[PackageValidatorService.validateZip] start, file={}", zipFile.getName());
        List<ValidationIssue> errors = new ArrayList<>();
        List<ValidationIssue> warnings = new ArrayList<>();
        Map<String, Integer> capabilities = new HashMap<>(Map.of("skills", 0, "agents", 0, "commands", 0, "mcp", 0, "hooks", 0));

        // 1. File size
        if (zipFile.length() > MAX_PACKAGE_SIZE) {
            errors.add(error("PACKAGE_TOO_LARGE", "Package exceeds 50MB limit", null));
        }

        // 2. Safe unzip to temp dir
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("dnacloud-validate-");
        } catch (IOException e) {
            errors.add(error("EXTRACT_FAILED", "Failed to create temp directory", null));
            return buildReport(errors, warnings, capabilities);
        }

        try {
            safeUnzip(zipFile, tempDir, errors);
            if (!errors.isEmpty()) {
                return buildReport(errors, warnings, capabilities);
            }

            // 3. manifest.json
            Path manifestPath = findManifest(tempDir);
            if (manifestPath == null) {
                errors.add(error("MISSING_MANIFEST", "manifest.json is required", null));
                return buildReport(errors, warnings, capabilities);
            }
            validateManifest(manifestPath, errors, warnings);

            // 4. install-plan.json
            if (!Files.exists(tempDir.resolve("install-plan.json"))) {
                errors.add(error("MISSING_INSTALL_PLAN", "install-plan.json is required", null));
            }

            // 5. Capability components
            scanCapabilities(tempDir, capabilities, warnings);

            // 6. File allowlist + secret scan
            scanFiles(tempDir, errors, warnings);

        } catch (Exception e) {
            log.error("[PackageValidatorService.validateZip] unexpected error, error={}", e.getMessage(), e);
            errors.add(error("VALIDATION_ERROR", "Unexpected validation error: " + e.getMessage(), null));
        } finally {
            deleteQuietly(tempDir.toFile());
        }

        log.info("[PackageValidatorService.validateZip] end, errors={}, warnings={}", errors.size(), warnings.size());
        return buildReport(errors, warnings, capabilities);
    }

    private void safeUnzip(File zipFile, Path destDir, List<ValidationIssue> errors) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // Path traversal check
                if (name.contains("..") || name.startsWith("/")) {
                    errors.add(error("PATH_TRAVERSAL", "Unsafe path in zip: " + name, name));
                    zis.closeEntry();
                    continue;
                }

                Path target = destDir.resolve(name).normalize();
                if (!target.startsWith(destDir)) {
                    errors.add(error("PATH_TRAVERSAL", "Path traversal attempt: " + name, name));
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    if (entry.getSize() > MAX_FILE_SIZE) {
                        errors.add(error("FILE_TOO_LARGE", "File exceeds 5MB: " + name, name));
                        zis.closeEntry();
                        continue;
                    }
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private Path findManifest(Path dir) throws IOException {
        // Could be at root or inside a single-folder wrapper
        Path direct = dir.resolve("manifest.json");
        if (Files.exists(direct)) return direct;
        // Check one level deep
        try (var stream = Files.list(dir)) {
            for (Path sub : stream.toList()) {
                if (Files.isDirectory(sub)) {
                    Path nested = sub.resolve("manifest.json");
                    if (Files.exists(nested)) return nested;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void validateManifest(Path manifestPath, List<ValidationIssue> errors, List<ValidationIssue> warnings) {
        try {
            Map<String, Object> manifest = objectMapper.readValue(manifestPath.toFile(), Map.class);

            String id = (String) manifest.get("id");
            if (id == null || !id.matches("^[a-z0-9-]+$")) {
                errors.add(error("INVALID_ID", "manifest.id must be lowercase alphanumeric with hyphens", "manifest.json"));
            }
            if (manifest.get("name") == null) {
                errors.add(error("MISSING_NAME", "manifest.name is required", "manifest.json"));
            }
            String version = (String) manifest.get("version");
            if (version == null || !version.matches("^\\d+\\.\\d+\\.\\d+.*$")) {
                errors.add(error("INVALID_VERSION", "manifest.version must be valid semver", "manifest.json"));
            }

            // packageType
            String packageType = (String) manifest.get("packageType");
            if (packageType == null || !ALLOWED_PACKAGE_TYPES.contains(packageType)) {
                errors.add(error("INVALID_PACKAGE_TYPE",
                    "manifest.packageType must be one of: " + ALLOWED_PACKAGE_TYPES + ". Got: " + packageType,
                    "manifest.json"));
            }

            // components must be an object (not an array)
            Object components = manifest.get("components");
            if (components == null) {
                errors.add(error("MISSING_COMPONENTS",
                    "manifest.components is required. Must be an object with keys: skills, agents, commands, mcp, hooks, rules",
                    "manifest.json"));
            } else if (components instanceof List) {
                errors.add(error("INVALID_COMPONENTS_FORMAT",
                    "manifest.components must be an object (not an array). Expected: {\"skills\":[...],\"agents\":[...],\"commands\":[...],\"mcp\":[...],\"hooks\":[...],\"rules\":[...]}",
                    "manifest.json"));
            }

            // Price
            Map<String, Object> price = (Map<String, Object>) manifest.get("price");
            if (price == null || price.get("amount") == null || price.get("currency") == null || price.get("network") == null) {
                errors.add(error("MISSING_PRICE", "manifest.price.amount/currency/network are required", "manifest.json"));
            }

            // Payout (required for uploads)
            Map<String, Object> payout = (Map<String, Object>) manifest.get("payout");
            if (payout == null) {
                errors.add(error("MISSING_PAYOUT", "manifest.payout is required for uploads", "manifest.json"));
            } else {
                String network = (String) payout.get("network");
                String currency = (String) payout.get("currency");
                String address = (String) payout.get("address");
                if (address == null || !address.startsWith("0x")) {
                    errors.add(error("INVALID_PAYOUT_ADDRESS", "payout.address must be a valid EVM address", "manifest.json"));
                }
                if (network == null || !SUPPORTED_NETWORKS.contains(network)) {
                    errors.add(error("UNSUPPORTED_PAYOUT_NETWORK", "payout.network must be one of: " + SUPPORTED_NETWORKS, "manifest.json"));
                }
                if (currency == null || !SUPPORTED_CURRENCIES.contains(currency)) {
                    errors.add(error("UNSUPPORTED_PAYOUT_CURRENCY", "payout.currency must be one of: " + SUPPORTED_CURRENCIES, "manifest.json"));
                }
            }

        } catch (Exception e) {
            errors.add(error("INVALID_MANIFEST_JSON", "manifest.json parse error: " + e.getMessage(), "manifest.json"));
        }
    }

    private void scanCapabilities(Path dir, Map<String, Integer> caps, List<ValidationIssue> warnings) throws IOException {
        // Skills
        Path skillsDir = dir.resolve("skills");
        if (Files.exists(skillsDir)) {
            try (var stream = Files.list(skillsDir)) {
                for (Path skill : stream.toList()) {
                    if (Files.isDirectory(skill) && Files.exists(skill.resolve("SKILL.md"))) {
                        caps.merge("skills", 1, Integer::sum);
                    }
                }
            }
        }

        // Agents
        Path agentsDir = dir.resolve("agents");
        if (Files.exists(agentsDir)) {
            try (var stream = Files.list(agentsDir)) {
                caps.put("agents", (int) stream.filter(p -> p.toString().endsWith(".md")).count());
            }
        }

        // Commands
        Path commandsDir = dir.resolve("commands");
        if (Files.exists(commandsDir)) {
            try (var stream = Files.list(commandsDir)) {
                caps.put("commands", (int) stream.filter(p -> p.toString().endsWith(".md")).count());
            }
        }

        // MCP
        Path mcpDir = dir.resolve("mcp");
        if (Files.exists(mcpDir)) {
            try (var stream = Files.list(mcpDir)) {
                caps.put("mcp", (int) stream.filter(p -> p.toString().endsWith(".json")).count());
            }
        }

        // Hooks
        if (Files.exists(dir.resolve("hooks").resolve("hooks.json"))) {
            caps.put("hooks", 1);
            warnings.add(warning("HOOKS_PRESENT", "This package installs Claude Code hooks. Buyer confirmation will be required.", "hooks/hooks.json"));
        }

        int total = caps.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            warnings.add(warning("LOW_CAPABILITY", "No skills/agents/commands/mcp/hooks found. Limited runtime effect.", null));
        }
    }

    private void scanFiles(Path dir, List<ValidationIssue> errors, List<ValidationIssue> warnings) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String name = file.getFileName().toString();
                String ext = name.contains(".") ? name.substring(name.lastIndexOf(".")).toLowerCase() : "";
                String rel = dir.relativize(file).toString();

                if (!ALLOWED_EXTENSIONS.contains(ext)) {
                    errors.add(error("DISALLOWED_FILE_TYPE", "File type not allowed: " + rel, rel));
                    return FileVisitResult.CONTINUE;
                }

                // Skip binary files and large files for content scan
                if (attrs.size() > 100_000) return FileVisitResult.CONTINUE;

                try {
                    String content = Files.readString(file);
                    for (Pattern p : SECRET_PATTERNS) {
                        if (p.matcher(content).find()) {
                            errors.add(error("SECRET_DETECTED", "Possible secret detected by pattern: " + p.pattern().substring(0, 20) + "...", rel));
                        }
                    }
                    for (Pattern p : DANGER_PATTERNS) {
                        if (p.matcher(content).find()) {
                            errors.add(error("DANGEROUS_PATTERN", "Dangerous pattern detected: " + p.pattern().substring(0, 20) + "...", rel));
                        }
                    }
                } catch (IOException ignored) {
                    // binary or unreadable
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    private ValidationReport buildReport(List<ValidationIssue> errors, List<ValidationIssue> warnings, Map<String, Integer> caps) {
        String result = !errors.isEmpty() ? "failed"
            : !warnings.isEmpty() ? "passed_with_warnings"
            : "passed";
        int score = Math.max(0, Math.min(100, 100 - errors.size() * 20 - warnings.size() * 5));
        return ValidationReport.builder()
            .result(result)
            .score(score)
            .errors(errors)
            .warnings(warnings)
            .skillCount(caps.getOrDefault("skills", 0))
            .agentCount(caps.getOrDefault("agents", 0))
            .commandCount(caps.getOrDefault("commands", 0))
            .mcpCount(caps.getOrDefault("mcp", 0))
            .hookCount(caps.getOrDefault("hooks", 0))
            .build();
    }

    private static ValidationIssue error(String code, String message, String file) {
        return ValidationIssue.builder().code(code).message(message).file(file).severity("error").build();
    }

    private static ValidationIssue warning(String code, String message, String file) {
        return ValidationIssue.builder().code(code).message(message).file(file).severity("warning").build();
    }

    private void deleteQuietly(File file) {
        try {
            Files.walkFileTree(file.toPath(), new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                    Files.delete(f); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                    Files.delete(d); return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
    }
}
