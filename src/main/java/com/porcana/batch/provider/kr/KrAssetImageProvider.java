package com.porcana.batch.provider.kr;

import com.porcana.domain.asset.entity.Asset;

import java.util.Optional;

public interface KrAssetImageProvider {

    Optional<ImageCandidate> findImage(Asset asset);

    boolean isConfigured();

    record ImageCandidate(String imageUrl, String source) {
    }
}
