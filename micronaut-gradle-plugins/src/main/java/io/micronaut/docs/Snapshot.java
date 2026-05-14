package io.micronaut.docs;

import java.util.Objects;

public class Snapshot implements Comparable<Snapshot> {

    private final String text;

    public Snapshot(String text) {
        this.text = text;
    }

    public int getMilestoneVersion() {
        return Integer.parseInt(text.replace("M", ""));
    }

    public int getReleaseCandidateVersion() {
        return Integer.parseInt(text.replace("RC", ""));
    }

    public boolean isBuildSnapshot() {
        return "SNAPSHOT".equals(text);
    }

    public boolean isReleaseCandidate() {
        return text.startsWith("RC");
    }

    public boolean isMilestone() {
        return text.startsWith("M");
    }

    @Override
    public int compareTo(Snapshot other) {
        if (isBuildSnapshot() && !other.isBuildSnapshot()) {
            return 1;
        }
        if (!isBuildSnapshot() && other.isBuildSnapshot()) {
            return -1;
        }
        if (isBuildSnapshot()) {
            return 0;
        }

        if (isReleaseCandidate() && !other.isReleaseCandidate()) {
            return 1;
        }
        if (!isReleaseCandidate() && other.isReleaseCandidate()) {
            return -1;
        }
        if (isReleaseCandidate()) {
            return Integer.compare(getReleaseCandidateVersion(), other.getReleaseCandidateVersion());
        }

        if (isMilestone() && !other.isMilestone()) {
            return 1;
        }
        if (!isMilestone() && other.isMilestone()) {
            return -1;
        }
        if (isMilestone()) {
            return Integer.compare(getMilestoneVersion(), other.getMilestoneVersion());
        }

        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Snapshot snapshot)) {
            return false;
        }
        return Objects.equals(text, snapshot.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public String toString() {
        return "Snapshot{"
            + "text='" + text + '\''
            + '}';
    }
}
