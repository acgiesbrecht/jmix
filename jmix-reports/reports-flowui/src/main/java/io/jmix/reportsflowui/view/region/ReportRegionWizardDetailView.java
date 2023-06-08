package io.jmix.reportsflowui.view.region;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.core.Metadata;
import io.jmix.flowui.Actions;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.action.list.ItemTrackingAction;
import io.jmix.flowui.action.list.ListDataComponentAction;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.kit.action.BaseAction;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionPropertyContainer;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;
import io.jmix.reports.entity.wizard.EntityTreeNode;
import io.jmix.reports.entity.wizard.RegionProperty;
import io.jmix.reports.entity.wizard.ReportRegion;
import io.jmix.reportsflowui.view.EntityTreeComposite;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

@Route(value = "reportregion/:id", layout = DefaultMainViewParent.class)
@ViewController("report_ReportRegionWizard.detail")
@ViewDescriptor("report-region-wizard-detail-view.xml")
@EditedEntityContainer("reportRegionDc")
public class ReportRegionWizardDetailView extends StandardDetailView<ReportRegion> {

    protected boolean isTabulated;//if true then user perform add tabulated region action
    protected boolean asFetchPlanEditor;
    protected boolean updatePermission = true;

    @ViewComponent
    protected Label tipLabel;
    @ViewComponent
    protected JmixButton upItem;
    @ViewComponent
    protected JmixButton downItem;
    @ViewComponent
    protected JmixButton addItem;
    @ViewComponent
    protected CollectionPropertyContainer<RegionProperty> reportRegionPropertiesTableDc;
    @ViewComponent
    protected FormLayout treePanel;
    @ViewComponent
    protected DataGrid<RegionProperty> propertiesTable;

    @Autowired
    private UiComponents uiComponents;
    @Autowired
    protected Messages messages;
    @Autowired
    protected Notifications notifications;
    @Autowired
    protected Actions actions;
    @Autowired
    protected Metadata metadata;

    protected TreeDataGrid<EntityTreeNode> entityTree;
    protected EntityTreeNode rootEntity;
    protected boolean scalarOnly = false;
    protected boolean collectionsOnly = false;
    protected boolean persistentOnly = false;

    public void setParameters(EntityTreeNode rootEntity, boolean scalarOnly, boolean collectionsOnly, boolean persistentOnly) {
        this.rootEntity = rootEntity;
        this.scalarOnly = scalarOnly;
        this.collectionsOnly = collectionsOnly;
        this.persistentOnly = persistentOnly;
        initTipLabel();
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        initComponents();
    }

    @Install(to = "propertiesTable.upItemAction", subject = "enabledRule")
    protected boolean propertiesTableUpEnabledRule() {
        RegionProperty selectedItem = propertiesTable.getSingleSelectedItem();
        return selectedItem != null && selectedItem.getOrderNum() > 1 && isUpdatePermitted();
    }
    @Install(to = "propertiesTable.downItemAction", subject = "enabledRule")
    protected boolean propertiesTableDownEnabledRule() {
        RegionProperty selectedItem = propertiesTable.getSingleSelectedItem();
        return selectedItem != null && selectedItem.getOrderNum() < reportRegionPropertiesTableDc.getItems().size() && isUpdatePermitted();
    }

    @Subscribe("propertiesTable.upItemAction")
    protected void onPropertiesTableUp(ActionPerformedEvent event) {
        replaceParameters(true);
    }

    @Subscribe("propertiesTable.downItemAction")
    protected void onPropertiesTableDown(ActionPerformedEvent event) {
        replaceParameters(false);
    }

    protected void replaceParameters(boolean up) {
        if (propertiesTable.getSingleSelectedItem() != null) {
            List<RegionProperty> items = reportRegionPropertiesTableDc.getMutableItems();
            int currentPosition = items.indexOf(propertiesTable.getSingleSelectedItem());
            if ((up && currentPosition != 0)
                    || (!up && currentPosition != items.size() - 1)) {
                int itemToSwapPosition = currentPosition - (up ? 1 : -1);

                Collections.swap(items, itemToSwapPosition, currentPosition);
            }
        }
    }

    protected void initTipLabel() {
        String messageKey = isTabulated
                ? "selectEntityPropertiesForTableArea"
                : "selectEntityProperties";
        tipLabel.setText(messages.formatMessage(messageKey, rootEntity.getLocalizedName()));
    }

    protected void initComponents() {
        initEntityTree();
    }

    protected void initEntityTree() {
        EntityTreeComposite entityTreeComposite = uiComponents.create(EntityTreeComposite.class);
        entityTreeComposite.setVisible(true);
        entityTreeComposite.setParameters(rootEntity, scalarOnly, collectionsOnly, persistentOnly);

        entityTree = entityTreeComposite.getEntityTree();
        entityTree.expand(rootEntity);

        BaseAction doubleClickAction = new BaseAction("doubleClick")
                .withHandler(event -> addProperty());
        doubleClickAction.setEnabled(isUpdatePermitted());
        entityTree.addAction(doubleClickAction);
        //todo normal double click registration
        entityTree.addItemClickListener(event -> {
            if (event.getClickCount() > 1) {
                entityTree.select(event.getItem());
                addProperty();
            }
        });

        ListDataComponentAction addPropertyAction = actions.create(ItemTrackingAction.ID, "addItemAction");
        addPropertyAction.addActionPerformedListener(event -> addProperty());
        addPropertyAction.addEnabledRule(this::isUpdatePermitted);
        entityTree.addAction(addPropertyAction);
        addItem.setAction(addPropertyAction);
        addItem.setIcon(VaadinIcon.ARROW_RIGHT.create());
        treePanel.add(entityTreeComposite);
    }

    protected void addProperty() {
        List<EntityTreeNode> nodesList = reportRegionPropertiesTableDc.getItems()
                .stream()
                .map(RegionProperty::getEntityTreeNode).toList();


        Set<EntityTreeNode> alreadyAddedNodes = new HashSet<>(nodesList);

        Set<EntityTreeNode> selectedItems = entityTree.getSelectedItems();
        List<RegionProperty> addedItems = new ArrayList<>();
        boolean alreadyAdded = false;
        for (EntityTreeNode entityTreeNode : selectedItems) {
            if (entityTreeNode.getMetaClassName() != null) {
                continue;
            }
            if (!alreadyAddedNodes.contains(entityTreeNode)) {
                RegionProperty regionProperty = metadata.create(RegionProperty.class);
                regionProperty.setEntityTreeNode(entityTreeNode);
                regionProperty.setOrderNum((long) reportRegionPropertiesTableDc.getItems().size() + 1);
                //first element must be not zero cause later we do sorting by multiplying that values
                reportRegionPropertiesTableDc.getMutableItems().add(regionProperty);
                addedItems.add(regionProperty);
            } else {
                alreadyAdded = true;
            }
        }
        if (addedItems.isEmpty()) {
            if (alreadyAdded) {
                notifications.create(messages.getMessage(getClass(), "elementsAlreadyAdded"))
                        .show();
            } else if (selectedItems.size() != 0) {
                notifications.create(messages.getMessage(getClass(), "selectPropertyFromEntity"))
                        .show();
            } else {
                notifications.create(messages.getMessage(getClass(), "elementsWasNotAdded"))
                        .show();
            }
        } else {
            propertiesTable.select(addedItems);
        }
    }

    @Install(to = "propertiesTable.removeItemAction", subject = "enabledRule")
    private boolean propertiesTableRemoveItemActionEnabledRule() {
        return isUpdatePermitted();
    }

    protected boolean isUpdatePermitted() {
        return updatePermission;
    }

    @Subscribe("propertiesTable.removeItemAction")
    public void onPropertiesTableRemoveItemAction(ActionPerformedEvent event) {
        for (RegionProperty item : propertiesTable.getSelectedItems()) {
            reportRegionPropertiesTableDc.getMutableItems().remove(item);
            normalizeRegionPropertiesOrderNum();
        }
    }

    protected void normalizeRegionPropertiesOrderNum() {
        long normalizedIdx = 0;
        List<RegionProperty> allItems = new ArrayList<>(reportRegionPropertiesTableDc.getItems());
        for (RegionProperty item : allItems) {
            item.setOrderNum(++normalizedIdx); //first must be 1
        }
    }

    @Subscribe(id = "reportRegionPropertiesTableDc", target = Target.DATA_CONTAINER)
    public void onReportRegionPropertiesTableDcCollectionChange(CollectionContainer.CollectionChangeEvent<ReportRegion> event) {
        showOrHideSortBtns();
    }

    @Subscribe(id = "reportRegionPropertiesTableDc", target = Target.DATA_CONTAINER)
    public void onReportRegionPropertiesTableDcItemChange(InstanceContainer.ItemChangeEvent<ReportRegion> event) {
        showOrHideSortBtns();
    }

    protected void showOrHideSortBtns() {
        if (propertiesTable.getSelectedItems().size() == reportRegionPropertiesTableDc.getItems().size() ||
                propertiesTable.getSelectedItems().size() == 0) {
            upItem.setEnabled(false);
            downItem.setEnabled(false);
        } else {
            upItem.setEnabled(isUpdatePermitted());
            downItem.setEnabled(isUpdatePermitted());
        }
    }

    @Subscribe
    public void onBeforeSave(BeforeSaveEvent event) {
        if (reportRegionPropertiesTableDc.getItems().isEmpty()) {
            notifications.create(messages.getMessage("selectAtLeastOneProp"))
                    .show();
            event.preventSave();
        }
    }

    @Install(to = "propertiesTable.upItemAction", subject = "enabledRule")
    private boolean propertiesTableUpItemActionEnabledRule() {
        return isUpdatePermitted();
    }

    @Install(to = "propertiesTable.downItemAction", subject = "enabledRule")
    private boolean propertiesTableDownItemActionEnabledRule() {
        return isUpdatePermitted();
    }
}