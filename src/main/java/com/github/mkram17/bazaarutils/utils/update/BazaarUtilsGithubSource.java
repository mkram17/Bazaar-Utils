package com.github.mkram17.bazaarutils.utils.update;

import moe.nea.libautoupdate.GithubReleaseUpdateSource;
import moe.nea.libautoupdate.UpdateData;

import java.util.List;
import java.util.stream.Collectors;

public class BazaarUtilsGithubSource extends GithubReleaseUpdateSource {
    public BazaarUtilsGithubSource() {
        super("mkram17", "Bazaar-Utils");
    }

    @Override
    protected UpdateData selectUpdate(String updateStream, List<GithubRelease> releases) {
        return findLatestRelease(releases.stream().filter(release -> {
            if (release.isDraft()) return false;

            String tag = release.getTagName().toLowerCase();
            boolean isAlpha = tag.contains("alpha");
            boolean isBeta = tag.contains("beta");

            return switch (updateStream.toLowerCase()) {
                case "alpha" -> true;
                case "beta" -> !isAlpha;
                default -> !isAlpha && !isBeta;
            };
        }).collect(Collectors.toList()));
    }
}
