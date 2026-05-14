package io.micronaut.docs.dropdown;

import java.util.StringJoiner;

public class SoftwareVersion implements Comparable<SoftwareVersion> {

    private int major;
    private int minor;
    private int patch;
    private Snapshot snapshot;
    private String versionText;

    public static SoftwareVersion build(String version) {
        String[] parts = version.split("\\.");
        SoftwareVersion softwareVersion = null;
        if (parts.length >= 3) {
            softwareVersion = new SoftwareVersion();
            softwareVersion.setVersionText(version);
            softwareVersion.setMajor(Integer.parseInt(parts[0]));
            softwareVersion.setMinor(Integer.parseInt(parts[1]));
            if (parts.length > 3) {
                softwareVersion.setSnapshot(new Snapshot(parts[3]));
            } else if (parts[2].contains("-")) {
                String[] subparts = parts[2].split("-");
                softwareVersion.setPatch(Integer.parseInt(subparts[0]));
                StringJoiner snapshotText = new StringJoiner("-");
                for (int i = 1; i < subparts.length; i++) {
                    snapshotText.add(subparts[i]);
                }
                softwareVersion.setSnapshot(new Snapshot(snapshotText.toString()));
                return softwareVersion;
            }
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
        } else if (!isSnapshot() && other.isSnapshot()) {
            return 1;
        } else if (isSnapshot()) {
            return snapshot.compareTo(other.snapshot);
        }
        return 0;
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
