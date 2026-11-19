package com.practice.aulaequilibrio.util;

import com.practice.aulaequilibrio.model.Permission;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;

public final class UiAccess {
    private UiAccess() {}
    public static boolean can(Permission permission) {
        var user = SessionContext.current();
        return user != null && !user.mustChangePassword() && user.role().allows(permission);
    }
    public static void apply(Parent root, Permission writePermission) {
        if (can(writePermission)) return;
        hideTagged(root);
    }
    private static void hideTagged(Node node) {
        if (node.getStyleClass().contains("requires-edit")) { node.setVisible(false); node.setManaged(false); }
        if (node instanceof SplitPane split) split.getItems().forEach(UiAccess::hideTagged);
        if (node instanceof ScrollPane scroll && scroll.getContent() != null) hideTagged(scroll.getContent());
        if (node instanceof TabPane tabs) tabs.getTabs().stream().filter(tab -> tab.getContent() != null).forEach(tab -> hideTagged(tab.getContent()));
        if (node instanceof ToolBar toolbar) toolbar.getItems().forEach(UiAccess::hideTagged);
        if (node instanceof Parent parent) parent.getChildrenUnmodifiable().forEach(UiAccess::hideTagged);
    }
}
