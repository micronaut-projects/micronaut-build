package io.micronaut.docs.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LegacyTocStrategy {
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^(\\S+?)\\.?\\s(.+)");

    public UserGuideNode generateToc(List<File> files) {
        List<File> sortedFiles = files == null ? new ArrayList<>() : new ArrayList<>(files);
        sortedFiles.sort(Comparator.comparing(LegacyTocStrategy::sectionNumbers, LegacyTocStrategy::compareSectionNumbers));

        UserGuideNode book = new UserGuideNode();
        for (File file : sortedFiles) {
            String chapter = file.getName().substring(0, file.getName().length() - 5);
            UserGuideNode section = new UserGuideNode();
            section.setName(chapter);
            section.setTitle(chapter);
            section.setFile(file.getName());

            int level = 0;
            Matcher matcher = CHAPTER_PATTERN.matcher(chapter);
            if (matcher.matches()) {
                level = matcher.group(1).split("\\.").length - 1;
                section.setTitle(matcher.group(2));
            }

            UserGuideNode parent = book;
            for (int i = 0; i < level; i++) {
                List<UserGuideNode> children = parent.getChildren();
                parent = children.get(children.size() - 1);
            }
            section.setParent(parent);
            parent.getChildren().add(section);
        }

        return book;
    }

    private static List<Integer> sectionNumbers(File file) {
        String name = file.getName();
        int spaceIndex = name.indexOf(' ');
        String index = spaceIndex > -1 ? name.substring(0, spaceIndex) : name;
        String[] tokens = index.split("\\.");
        List<Integer> numbers = new ArrayList<>();
        for (String token : tokens) {
            if (!token.trim().isEmpty()) {
                numbers.add(Integer.parseInt(token));
            }
        }
        return numbers;
    }

    private static int compareSectionNumbers(List<Integer> left, List<Integer> right) {
        List<Integer> leftNumbers = new ArrayList<>(left);
        List<Integer> rightNumbers = new ArrayList<>(right);
        while (leftNumbers.size() < rightNumbers.size()) {
            leftNumbers.add(0);
        }
        while (rightNumbers.size() < leftNumbers.size()) {
            rightNumbers.add(0);
        }
        for (int i = 0; i < leftNumbers.size(); i++) {
            int result = leftNumbers.get(i).compareTo(rightNumbers.get(i));
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }
}
