package com.campuslink.common.markdown;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;

/** Canonical local image identifiers, extracted only from already sanitized HTML. */
public final class PostImageUrls {
    private static final Pattern PATH = Pattern.compile(
            "^/api/v1/post-images/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/content$");
    private PostImageUrls() { }

    public static String url(UUID id) {
        return "/api/v1/post-images/" + id + "/content";
    }

    public static boolean managed(String value) {
        return value != null && PATH.matcher(value).matches();
    }

    public static Set<UUID> references(String safeHtml) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (var image : Jsoup.parseBodyFragment(safeHtml == null ? "" : safeHtml).select("img[src]")) {
            var match = PATH.matcher(image.attr("src"));
            if (match.matches()) {
                ids.add(UUID.fromString(match.group(1)));
            }
        }
        return ids;
    }

    public static String firstImage(String safeHtml) {
        for (var image : Jsoup.parseBodyFragment(safeHtml == null ? "" : safeHtml).select("img[src]")) {
            String source = image.attr("src");
            if (managed(source) || source.startsWith("https://")) {
                return source;
            }
        }
        return null;
    }
}
