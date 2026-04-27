package com.porcana.batch.provider.kr;

import com.porcana.domain.asset.entity.Asset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class CsvKrAssetImageProvider implements KrAssetImageProvider {

    private final ResourceLoader resourceLoader;
    private final String catalogPath;

    private volatile Map<String, ImageCandidate> cachedCatalog;

    public CsvKrAssetImageProvider(
            ResourceLoader resourceLoader,
            @Value("${batch.provider.kr.image-catalog-path:classpath:batch/kr_asset_images.csv}") String catalogPath
    ) {
        this.resourceLoader = resourceLoader;
        this.catalogPath = catalogPath;
    }

    @Override
    public Optional<ImageCandidate> findImage(Asset asset) {
        if (asset == null || asset.getSymbol() == null || asset.getSymbol().isBlank()) {
            return Optional.empty();
        }

        ImageCandidate candidate = loadCatalog().get(asset.getSymbol().trim());
        if (candidate == null || !isValidImageUrl(candidate.imageUrl())) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    @Override
    public boolean isConfigured() {
        return resourceLoader.getResource(catalogPath).exists();
    }

    Map<String, ImageCandidate> loadCatalog() {
        if (cachedCatalog != null) {
            return cachedCatalog;
        }

        synchronized (this) {
            if (cachedCatalog != null) {
                return cachedCatalog;
            }

            Map<String, ImageCandidate> catalog = new HashMap<>();
            Resource resource = resourceLoader.getResource(catalogPath);

            if (!resource.exists()) {
                log.warn("KR image catalog not found: {}", catalogPath);
                cachedCatalog = catalog;
                return cachedCatalog;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                boolean firstLine = true;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    if (firstLine && trimmed.toLowerCase(Locale.ROOT).startsWith("symbol,")) {
                        firstLine = false;
                        continue;
                    }
                    firstLine = false;

                    String[] parts = trimmed.split(",", 3);
                    if (parts.length < 2) {
                        continue;
                    }

                    String symbol = parts[0].trim();
                    String imageUrl = parts[1].trim();
                    String source = parts.length >= 3 ? parts[2].trim() : "manual";

                    if (symbol.isEmpty() || imageUrl.isEmpty()) {
                        continue;
                    }

                    if (!isValidImageUrl(imageUrl)) {
                        log.debug("Skipping invalid KR image URL for {}: {}", symbol, imageUrl);
                        continue;
                    }

                    catalog.put(symbol, new ImageCandidate(imageUrl, source));
                }
            } catch (Exception e) {
                log.warn("Failed to load KR image catalog {}: {}", catalogPath, e.getMessage());
            }

            log.info("Loaded {} KR image catalog entries from {}", catalog.size(), catalogPath);
            cachedCatalog = catalog;
            return cachedCatalog;
        }
    }

    boolean isValidImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }

        String normalized = imageUrl.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("defaultimage")
                || normalized.contains("placeholder")
                || normalized.contains("noimage")
                || normalized.contains("/default/")
                || normalized.endsWith("/default")) {
            return false;
        }

        try {
            URI uri = URI.create(imageUrl.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
        } catch (Exception e) {
            return false;
        }
    }
}
