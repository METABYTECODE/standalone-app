/*
 * Copyright 2017 Sam Sun <github-contact@samczsun.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.heliosdecompiler.helios.gui.controller;

import com.google.inject.Inject;
import com.heliosdecompiler.helios.controller.RecentFileController;
import com.heliosdecompiler.helios.controller.files.OpenedFile;
import com.heliosdecompiler.helios.controller.files.OpenedFileController;
import com.heliosdecompiler.helios.gui.model.TreeNode;
import com.heliosdecompiler.helios.ui.MessageHandler;
import com.heliosdecompiler.helios.utils.Utils;
import javafx.collections.MapChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;

import java.io.File;
import java.io.InputStream;
import java.util.*;

public class FileTreeController extends NestedController<MainViewController> {

    @FXML
    private TreeView<TreeNode> root;

    private TreeItem<TreeNode> rootItem;

    @Inject
    private MessageHandler messageHandler;

    @Inject
    private OpenedFileController openedFileController;

    @Inject
    private RecentFileController recentFileController;
    private Map<TreeNode, TreeItem<TreeNode>> itemMap = new HashMap<>();

    @FXML
    public void initialize() {
        this.rootItem = new TreeItem<>(new TreeNode("[root]"));
        this.root.setRoot(this.rootItem);
        this.root.setCellFactory(new TreeCellFactory<>());

        root.addEventHandler(KeyEvent.KEY_RELEASED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                TreeItem<TreeNode> selected = this.root.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    if (selected.getChildren().size() != 0) {
                        selected.setExpanded(!selected.isExpanded());

                    } else {
                        getParentController().getAllFilesViewerController().handleClick(selected.getValue());
                    }
                }
            }
        });

        openedFileController.loadedFiles().addListener((MapChangeListener<String, OpenedFile>) change -> {
            if (change.getValueAdded() != null) {
                updateTree(change.getValueAdded());
            }
            if (change.getValueRemoved() != null) {
                this.rootItem.getChildren().removeIf(ti -> ti.getValue().equals(change.getValueRemoved().getRoot()));
            }
        });
    }

    @FXML
    public void onClickTreeItem(MouseEvent event) {
        if (event.getClickCount() == 2) {
            if (getParentController().getAllFilesViewerController().handleClick(this.root.getSelectionModel().getSelectedItem().getValue())) {
                event.consume();
            }
        }
    }

    @FXML
    public void startDrop(DragEvent event) {
        Dragboard db = event.getDragboard();
        if (db.hasFiles()) {
            event.acceptTransferModes(TransferMode.ANY);
        }
    }

    @FXML
    public void stopDrop(DragEvent event) {
        Dragboard db = event.getDragboard();
        boolean success = false;
        if (db.hasFiles()) {
            success = true;
            for (File file : db.getFiles()) {
                if (file.getName().endsWith(".jar")) {
                    openedFileController.openFile(file);
                }
            }
        }
        event.setDropCompleted(success);
        event.consume();
    }

    public InputStream getIconForTreeItem(TreeNode node) {
        if (node.testFlag(OpenedFile.IS_ROOT_FILE)) {
            if (node.getDisplayName().endsWith(".jar")) {
                return getClass().getResourceAsStream("/res/jar.png");
            } else {
                return getClass().getResourceAsStream("/res/file.png");
            }
        } else {
            if (node.getChildren().size() > 0) {
                return getClass().getResourceAsStream("/res/package.png");
            } else if (node.getDisplayName().endsWith(".class")) {
                return getClass().getResourceAsStream("/res/class.png");
            } else {
                return getClass().getResourceAsStream("/res/file.png");
            }
        }
    }

    public void reload() {
        this.openedFileController.reload(this);
    }

    public Collection<TreeNode> getRoots() {
        return new TreeItemNode(this.rootItem).getChildren();
    }

    public void updateTree(List<TreeNode> add, List<TreeNode> remove) {
        Set<TreeItem<TreeNode>> updated = new HashSet<>();
        ArrayDeque<TreeNode> queue = new ArrayDeque<>();
        queue.addAll(add);

        while (!queue.isEmpty()) {
            TreeNode thisNode = queue.pop();

            TreeItem<TreeNode> parent;

            if (thisNode.getParent() == null) {
                parent = rootItem;
            } else {
                parent = itemMap.get(thisNode.getParent());
            }

            updated.add(parent);

            TreeItem<TreeNode> thisItem = new TreeItem<>(thisNode);
            thisItem.addEventHandler(TreeItem.<TreeNode>branchExpandedEvent(), event -> {
                if (thisItem.getChildren().size() == 1) {
                    thisItem.getChildren().get(0).setExpanded(true);
                }
            });
            thisItem.setGraphic(new ImageView(new Image(getIconForTreeItem(thisNode))));
            parent.getChildren().add(thisItem);
            parent.getChildren().sort((a, b) -> {
                int ac = a.getValue().getChildren().size();
                int bc = b.getValue().getChildren().size();

                if (ac == 0 && bc != 0)
                    return 1;
                else if (ac != 0 && bc == 0)
                    return -1;
                return a.getValue().getDisplayName().compareTo(b.getValue().getDisplayName());
            });

            itemMap.put(thisNode, thisItem);

            queue.addAll(thisNode.getChildren());
        }

        queue.addAll(remove);

        while (!queue.isEmpty()) {
            TreeNode thisNode = queue.pop();
            TreeItem<TreeNode> thisItem = itemMap.remove(thisNode);
            thisItem.getParent().getChildren().remove(thisItem);
            queue.addAll(thisNode.getChildren());
        }
    }

    public void updateTree() {
        this.openedFileController.getLoadedFiles().forEach(this::updateTree);
    }

    private void updateTree(OpenedFile file) {
        TreeNode root = file.getRoot();
        Collection<TreeNode> displayedFiles = getRoots();

        List<TreeNode> remove = new ArrayList<>();
        List<TreeNode> add = new ArrayList<>();

        handleChanges(new State(root, displayedFiles), add);

        TreeNode want = Utils.find(root, displayedFiles);
        if (want != null) {
            handleChanges(new State(want, Collections.singletonList(root)), remove);
        }

        updateTree(add, remove);
    }

    private void handleChanges(State startingState, List<TreeNode> changes) {
        ArrayDeque<State> check = new ArrayDeque<>();
        check.add(startingState);
        while (!check.isEmpty()) {
            State next = check.pop();
            TreeNode match = Utils.find(next.needle, next.haystack);
            if (match != null) {
                for (TreeNode current : next.needle.getChildren()) {
                    check.add(new State(current, match.getChildren()));
                }
            } else {
                changes.add(next.needle);
            }
        }
    }

    private class TreeItemNode extends TreeNode {
        private TreeItem<TreeNode> thisItem;
        private volatile Collection<TreeNode> cached;

        public TreeItemNode(TreeItem<TreeNode> item) {
            super(item.getParent() == null || item.getParent() == rootItem ? null : item.getParent().getValue(), item.getValue().getDisplayName());
            thisItem = item;
        }

        public Collection<TreeNode> getChildren() {
            if (cached == null) {
                synchronized (this) {
                    if (cached == null) {
                        cached = new ArrayList<>();
                        for (TreeItem<TreeNode> item : thisItem.getChildren()) {
                            cached.add(new TreeItemNode(item));
                        }
                    }
                }
            }
            return cached;
        }
    }

    class State {
        TreeNode needle;
        Collection<TreeNode> haystack;

        public State(TreeNode needle, Collection<TreeNode> haystack) {
            this.needle = needle;
            this.haystack = haystack;
        }
    }
}
