package com.github.mkram17.bazaarutils.utils.update;

import moe.nea.libautoupdate.GithubReleaseUpdateSource;
import moe.nea.libautoupdate.UpdateData;

import java.util.List;

public class BazaarUtilsGithubSource extends GithubReleaseUpdateSource {
    public BazaarUtilsGithubSource() {
        super("mkram17", "Bazaar-Utils");
    }

    @Override
    protected UpdateData selectUpdate(String updateStream, List<GithubRelease> releases) {
        UpdateStream stream = UpdateStream.fromVersion(updateStream);

        return findLatestRelease(
                releases.stream()
                        .filter(release -> !release.isDraft())
                        .filter(release -> matchesStream(release.getTagName().toLowerCase(), stream))
                        .toList()
        );
    }

    private boolean matchesStream(String tag, UpdateStream stream) {
        boolean isAlpha = tag.contains("alpha");
        boolean isBeta  = tag.contains("beta");

        return switch (stream) {
            case ALPHA  -> true;     // alpha users receive all releases
            case BETA   -> !isAlpha; // beta users skip alpha
            case STABLE -> !isAlpha && !isBeta;
        };
    }
}
