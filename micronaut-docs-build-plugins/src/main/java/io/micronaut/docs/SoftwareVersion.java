package io.micronaut.docs;

import java.util.Arrays;
import java.util.Objects;

public class SoftwareVersion implements Comparable<SoftwareVersion> {

    private int major;
    private int minor;
    private int patch;
    private Snapshot snapshot;
    private String versionText;

    public static SoftwareVersion build(String version) {
        String[] parts = version.split("\\.");
        if (parts.length < 3) {
            return null;
        }

        SoftwareVersion softwareVersion = new SoftwareVersion();
        softwareVersion.setVersionText(version);
        softwareVersion.setMajor(Integer.parseInt(parts[0]));
        softwareVersion.setMinor(Integer.parseInt(parts[1]));
        if (parts.length > 3) {
            softwareVersion.setSnapshot(new Snapshot(parts[3]));
            softwareVersion.setPatch(Integer.parseInt(parts[2]));
        } else if (parts[2].contains("-")) {
            String[] subparts = parts[2].split("-");
            softwareVersion.setPatch(Integer.parseInt(subparts[0]));
            softwareVersion.setSnapshot(new Snapshot(String.join("-", Arrays.copyOfRange(subparts, 1, subparts.length))));
        } else {
            softwareVersion.setPatch(Integer.parseInt(parts[2]));
        }
        return softwareVersion;
    }

    public int getMajor() {
        return major;
    }

    public void setMajor(int major) {
        this.major = major;
    }

    public int getMinor() {
        return minor;
    }

    public void setMinor(int minor) {
        this.minor = minor;
    }

    public int getPatch() {
        return patch;
    }

    public void setPatch(int patch) {
        this.patch = patch;
    }

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(Snapshot snapshot) {
        this.snapshot = snapshot;
    }

    public String getVersionText() {
        return versionText;
    }

    public void setVersionText(String versionText) {
        this.versionText = versionText;
    }

    public boolean isSnapshot() {
        return snapshot != null;
    }

    @Override
    public int compareTo(SoftwareVersion other) {
        int majorCompare = Integer.compare(major, other.major);
        if (majorCompare != 0) {
            return majorCompare;
        }

        int minorCompare = Integer.compare(minor, other.minor);
        if (minorCompare != 0) {
            return minorCompare;
        }

        int patchCompare = Integer.compare(patch, other.patch);
        if (patchCompare != 0) {
            return patchCompare;
        }

        if (isSnapshot() && !other.isSnapshot()) {
            return -1;
        }
        if (!isSnapshot() && other.isSnapshot()) {
            return 1;
        }
        if (isSnapshot()) {
            return snapshot.compareTo(other.snapshot);
        }
        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SoftwareVersion that)) {
            return false;
        }
        return major == that.major
            && minor == that.minor
            && patch == that.patch
            && Objects.equals(snapshot, that.snapshot)
            && Objects.equals(versionText, that.versionText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, snapshot, versionText);
    }

    @Override
    public String toString() {
        return "SoftwareVersion{"
            + "major=" + major
            + ", minor=" + minor
            + ", patch=" + patch
            + ", snapshot=" + snapshot
            + ", versionText='" + versionText + '\''
            + '}';
    }
}
