package io.micronaut.docs.internal;

import java.util.ArrayList;
import java.util.List;

public class UserGuideNode {
    private UserGuideNode parent;
    private List<UserGuideNode> children = new ArrayList<>();
    private String name;
    private String title;
    private String file;

    public UserGuideNode getParent() {
        return parent;
    }

    public void setParent(UserGuideNode parent) {
        this.parent = parent;
    }

    public List<UserGuideNode> getChildren() {
        return children;
    }

    public void setChildren(List<UserGuideNode> children) {
        this.children = children;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    @Override
    public String toString() {
        return "UserGuideNode{"
            + "name='" + name + '\''
            + ", title='" + title + '\''
            + ", file='" + file + '\''
            + '}';
    }
}
