package com.golda.patchertiktok;

final class SeekbarPolicy {
    private SeekbarPolicy() {
    }

    static int normalizeShowType(int requestedType) {
        return requestedType == 3 || requestedType == 4 ? 0 : requestedType;
    }

    static boolean shouldForceShow(
            boolean originalResult,
            boolean hasVideo,
            boolean isAd,
            boolean isLive,
            boolean isPhoto
    ) {
        return originalResult || (hasVideo && !isAd && !isLive && !isPhoto);
    }
}
