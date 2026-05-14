package io.micronaut.docs.dropdown;

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
        return "BUILD-SNAPSHOT".equals(text);
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
        } else if (!isBuildSnapshot() && other.isBuildSnapshot()) {
            return -1;
        } else if (isBuildSnapshot()) {
            return 0;
        }

        if (isReleaseCandidate() && !other.isReleaseCandidate()) {
            return 1;
        } else if (!isReleaseCandidate() && other.isReleaseCandidate()) {
            return -1;
        } else if (isReleaseCandidate()) {
            return Integer.compare(getReleaseCandidateVersion(), other.getReleaseCandidateVersion());
        }

        if (isMilestone() && !other.isMilestone()) {
            return 1;
        } else if (!isMilestone() && other.isMilestone()) {
            return -1;
        } else if (isMilestone()) {
            return Integer.compare(getMilestoneVersion(), other.getMilestoneVersion());
        }

        return 0;
    }
}
