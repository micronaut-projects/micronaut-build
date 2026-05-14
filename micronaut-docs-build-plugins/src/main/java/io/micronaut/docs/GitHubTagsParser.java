package io.micronaut.docs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GitHubTagsParser {
    private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private GitHubTagsParser() {
    }

    public static List<SoftwareVersion> toVersions(String json) {
        if (json == null) {
            return Collections.emptyList();
        }

        List<SoftwareVersion> versions = new ArrayList<>();
        Matcher matcher = NAME_PATTERN.matcher(json);
        while (matcher.find()) {
            String tagName = unescapeJsonString(matcher.group(1));
            if (tagName.startsWith("v")) {
                SoftwareVersion version = SoftwareVersion.build(tagName.substring(1));
                if (version != null) {
                    versions.add(version);
                }
            }
        }

        versions.sort(Collections.reverseOrder());
        return versions;
    }

    private static String unescapeJsonString(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i == value.length() - 1) {
                result.append(current);
                continue;
            }
            char escaped = value.charAt(++i);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (i + 4 <= value.length() - 1) {
                        result.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                }
                default -> result.append(escaped);
            }
        }
        return result.toString();
    }
}
