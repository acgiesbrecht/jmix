package io.jmix.reportsflowui.view.template.edit;

import io.jmix.reportsflowui.ReportsUiHelper;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.jmix.core.Metadata;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.combobox.JmixComboBox;
import io.jmix.flowui.component.radiobuttongroup.JmixRadioButtonGroup;
import io.jmix.flowui.component.select.JmixSelect;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.component.upload.FileUploadField;
import io.jmix.flowui.kit.component.upload.event.FileUploadFailedEvent;
import io.jmix.flowui.kit.component.upload.event.FileUploadStartedEvent;
import io.jmix.flowui.kit.component.upload.event.FileUploadSucceededEvent;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.reports.ReportPrintHelper;
import io.jmix.reports.entity.CustomTemplateDefinedBy;
import io.jmix.reports.entity.Report;
import io.jmix.reports.entity.ReportOutputType;
import io.jmix.reports.entity.ReportTemplate;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import io.jmix.security.constraint.PolicyStore;
import io.jmix.security.constraint.SecureOperations;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static io.jmix.reportsflowui.ReportsUiHelper.FIELD_ICON_CLASS_NAME;
import static io.jmix.reportsflowui.ReportsUiHelper.FIELD_ICON_SIZE_CLASS_NAME;

@Route(value = "reportTemplate/:id", layout = DefaultMainViewParent.class)
@ViewController("report_ReportTemplate.detail")
@ViewDescriptor("report-template-detail-view.xml")
@EditedEntityContainer("reportTemplateDc")
@DialogMode(maxWidth = "40em")
public class ReportTemplateDetailView extends StandardDetailView<ReportTemplate> {

    public static final String CUSTOM_DEFINE_BY_PROPERTY = "customDefinedBy";
    public static final String CUSTOM_PROPERTY = "custom";
    public static final String REPORT_OUTPUT_TYPE_PROPERTY = "reportOutputType";

    @Autowired
    protected MessageBundle messageBundle;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private SecureOperations secureOperations;
    @Autowired
    private Metadata metadata;
    @Autowired
    private PolicyStore policyStore;
    @Autowired
    private Notifications notifications;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private ReportsUiHelper reportsUiHelper;

    @ViewComponent
    private InstanceContainer<ReportTemplate> reportTemplateDc;
    @ViewComponent
    private JmixRadioButtonGroup<Boolean> isGroovyRadioButtonGroup;
    @ViewComponent
    private JmixCheckbox customField;
    @ViewComponent
    private JmixSelect<CustomTemplateDefinedBy> customDefinedByField;
    @ViewComponent
    private JmixTextArea customDefinitionField;
    @ViewComponent
    private JmixCheckbox alterableField;
    @ViewComponent
    private FileUploadField templateUploadField;
    @ViewComponent
    private TypedTextField<String> outputNamePatternField;
    @ViewComponent
    private JmixTextArea templateFileEditor;
    @ViewComponent
    private JmixComboBox<ReportOutputType> outputTypeField;
    @ViewComponent
    private HorizontalLayout descriptionEditBox;
    @ViewComponent
    private VerticalLayout previewBox;

    protected Icon customDefinitionHelpIcon;
    protected Icon customDefinitionExpandIcon;

    protected TableEditFragment tableEditComposite;

    @Subscribe
    public void onInit(InitEvent event) {
        isGroovyRadioButtonGroup.setItems(Boolean.TRUE, Boolean.FALSE);
        isGroovyRadioButtonGroup.setItemLabelGenerator(item -> item
                ? messageBundle.getMessage("isGroovyRadioButtonGroup.groovyType")
                : messageBundle.getMessage("isGroovyRadioButtonGroup.freemarkerType"));
        initOutputTypeList();
        initOutputNamePatternField();
        initCustomDefinitionHelpIcon();
        initCustomDefinitionExpandIcon();
    }

    @Subscribe
    public void onInitEntity(InitEntityEvent<ReportTemplate> event) {
        ReportTemplate template = event.getEntity();
        if (StringUtils.isEmpty(template.getCode())) {
            Report report = template.getReport();
            if (report != null) {
                if (report.getTemplates() == null || report.getTemplates().isEmpty()) {
                    template.setCode(ReportTemplate.DEFAULT_TEMPLATE_CODE);
                } else {
                    template.setCode("Template_" + report.getTemplates().size());
                }
            }
        }
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        reportTemplateDc.addItemPropertyChangeListener(this::onReportTemplateDcItemPropertyChange);

        initDescriptionComposites();
        initUploadField();

        ReportTemplate reportTemplate = getEditedEntity();
        initTemplateEditor(reportTemplate);
        getDescriptionEditors().forEach(controller -> controller.setReportTemplate(reportTemplate));
        setupVisibility(reportTemplate.getCustom(), reportTemplate.getReportOutputType());
    }

    @Subscribe
    public void onBeforeSave(BeforeSaveEvent event) {
        if (!validateTemplateFile() || !validateInputOutputFormats()) {
            event.preventSave();
        }
        ReportTemplate reportTemplate = getEditedEntity();
        for (AbstractDescriptionEditFragment<?> editor : getDescriptionEditors()) {
            if (editor.isApplicable(reportTemplate.getReportOutputType())) {
                if (!editor.applyChanges()) {
                    event.preventSave();
                }
            }
        }

        if (!Boolean.TRUE.equals(reportTemplate.getCustom())) {
            reportTemplate.setCustomDefinition("");
        }

        String extension = FilenameUtils.getExtension(templateUploadField.getFileName());
        if (extension != null) {
            ReportOutputType outputType = ReportOutputType.getTypeFromExtension(extension.toUpperCase());
            if (hasHtmlCsvTemplateOutput(outputType)) {
                byte[] bytes = templateFileEditor.getValue() == null
                        ? new byte[0]
                        : templateFileEditor.getValue().getBytes(StandardCharsets.UTF_8);
                reportTemplate.setContent(bytes);
            }
        }
    }

    @Subscribe("templateUploadField")
    public void onTemplateUploadFieldFileUploadStarted(FileUploadStartedEvent<FileUploadField> event) {
        templateUploadField.setFileName(event.getFilename());
    }

    @Subscribe("templateUploadField")
    public void onTemplateUploadFieldFileUploadFailed(FileUploadFailedEvent<FileUploadField> event) {
        notifications.create(messageBundle.getMessage("notification.uploadFailed.header"))
                .withType(Notifications.Type.WARNING)
                .show();
    }

    @Subscribe("templateUploadField")
    public void onTemplateUploadFieldFileUploadSucceeded(FileUploadSucceededEvent<FileUploadField> event) {
        ReportTemplate reportTemplate = getEditedEntity();
        reportTemplate.setName(event.getFileName());
        reportTemplate.setContent(templateUploadField.getValue());

        initTemplateEditor(reportTemplate);

        setupTemplateTypeVisibility(hasTemplateOutput(reportTemplate.getReportOutputType()));

        updateOutputType();

        notifications.create(messageBundle.getMessage("notification.uploadSuccess.header"))
                .withPosition(Notification.Position.BOTTOM_END)
                .show();
    }

    protected void initCustomDefinitionHelpIcon() {
        customDefinitionHelpIcon = VaadinIcon.QUESTION_CIRCLE.create();
        customDefinitionHelpIcon.addClassNames(FIELD_ICON_SIZE_CLASS_NAME, FIELD_ICON_CLASS_NAME);
        customDefinitionHelpIcon.addClickListener(this::onCustomDefinitionHelpIconClick);
    }

    protected void initCustomDefinitionExpandIcon() {
        customDefinitionExpandIcon = VaadinIcon.EXPAND_SQUARE.create();
        customDefinitionExpandIcon.addClassNames(FIELD_ICON_SIZE_CLASS_NAME, FIELD_ICON_CLASS_NAME);
        customDefinitionExpandIcon.addClickListener(this::onExpandCustomDefinitionExpandIconClick);
    }

    protected void initDescriptionComposites() {
        tableEditComposite = uiComponents.create(TableEditFragment.class);
        tableEditComposite.setVisible(false);
        tableEditComposite.setPreviewBox(previewBox);
        Dialog target = UiComponentUtils.findDialog(this);
        if (target == null) {
            throw new IllegalStateException(ReportTemplate.class.getSimpleName()
                    + " detail view must be opened as dialog");
        }
        tableEditComposite.setTarget(target);
        descriptionEditBox.add(tableEditComposite);
    }

    protected void onCustomDefinitionHelpIconClick(ClickEvent<Icon> event) {
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage("customDefinitionField.helpIcon.dialog.header"))
                .withContent(new Html(messageBundle.getMessage("customDefinitionField.helpIcon.dialog.content")))
                .withResizable(true)
                .withModal(false)
                .withWidth("50em")
                .open();
    }

    protected void initOutputNamePatternField() {
        Icon icon = VaadinIcon.QUESTION_CIRCLE.create();
        icon.addClickListener(this::onOutputNamePatternHelpIconClick);
        icon.addClassName(FIELD_ICON_CLASS_NAME);
        outputNamePatternField.setSuffixComponent(icon);
    }

    protected void onOutputNamePatternHelpIconClick(ClickEvent<Icon> event) {
        dialogs.createMessageDialog()
                .withHeader(messageBundle.getMessage("outputNamePatternField.helpIcon.dialog.header"))
                .withContent(new Html(messageBundle.getMessage("outputNamePatternField.helpIcon.dialog.content")))
                .withResizable(true)
                .withModal(false)
                .withWidth("40em")
                .open();
    }

    protected void onExpandCustomDefinitionExpandIconClick(ClickEvent<Icon> event) {
        reportsUiHelper.showScriptEditorDialog(
                messageBundle.getMessage("customDefinitionField.label"),
                getEditedEntity().getCustomDefinition(),
                result -> getEditedEntity().setCustomDefinition(result), null);
    }

    protected void initUploadField() {
        ReportTemplate reportTemplate = getEditedEntity();
        byte[] templateFile = reportTemplate.getContent();
        if (templateFile != null) {
            templateUploadField.setFileName(reportTemplate.getName());
            templateUploadField.setValue(templateFile);
        }

        templateUploadField.setReadOnly(!isContentAttributeUpdatePermitted(reportTemplate));
    }

    protected void onReportTemplateDcItemPropertyChange
            (InstanceContainer.ItemPropertyChangeEvent<ReportTemplate> event) {
        ReportTemplate reportTemplate = getEditedEntity();
        switch (event.getProperty()) {
            case REPORT_OUTPUT_TYPE_PROPERTY: {
                ReportOutputType prevOutputType = (ReportOutputType) event.getPrevValue();
                ReportOutputType newOutputType = (ReportOutputType) event.getValue();
                setupVisibility(reportTemplate.getCustom(), newOutputType);
                if (hasHtmlCsvTemplateOutput(prevOutputType) && !hasTemplateOutput(newOutputType)) {
                    dialogs.createMessageDialog()
                            .withHeader(messageBundle.getMessage("reportTemplateDetailView.clearTemplateWarning.header"))
                            .withText(messageBundle.getMessage("reportTemplateDetailView.clearTemplateWarning.text"))
                            .open();
                }
                break;
            }
            case CUSTOM_PROPERTY: {
                setupVisibility(Boolean.TRUE.equals(event.getValue()), reportTemplate.getReportOutputType());
                break;
            }
            case CUSTOM_DEFINE_BY_PROPERTY: {
                boolean isGroovyScript = hasScriptCustomDefinedBy(reportTemplate.getCustomDefinedBy());
                setCustomDefinitionIconsVisible(isGroovyScript);
                break;
            }
        }
    }

    protected Collection<AbstractDescriptionEditFragment<?>> getDescriptionEditors() {
        return List.of(tableEditComposite);
    }

    protected void setCustomDefinitionIconsVisible(boolean visible) {
        customDefinitionField.setSuffixComponent(visible
                ? new Div(customDefinitionExpandIcon, customDefinitionHelpIcon)
                : null);
    }

    protected void initOutputTypeList() {
        ArrayList<ReportOutputType> outputTypes = new ArrayList<>(Arrays.asList(ReportOutputType.values()));

        // Unsupported types for now
        outputTypes.remove(ReportOutputType.CHART);
        outputTypes.remove(ReportOutputType.PIVOT_TABLE);

        outputTypeField.setItems(outputTypes);
    }

    protected void initTemplateEditor(ReportTemplate reportTemplate) {
        String extension = FilenameUtils.getExtension(templateUploadField.getFileName());
        if (extension == null) {
            visibleTemplateEditor(null);
            return;
        }
        ReportOutputType outputType = ReportOutputType.getTypeFromExtension(extension.toUpperCase());
        visibleTemplateEditor(outputType);
        if (hasHtmlCsvTemplateOutput(outputType)) {
            String templateContent = new String(reportTemplate.getContent(), StandardCharsets.UTF_8);
            templateFileEditor.setValue(templateContent);
        }
        templateFileEditor.setReadOnly(
                !secureOperations.isEntityUpdatePermitted(metadata.getClass(reportTemplate), policyStore));
    }

    protected void visibleTemplateEditor(@Nullable ReportOutputType outputType) {
        String extension = FilenameUtils.getExtension(templateUploadField.getFileName());
        if (extension == null) {
            templateFileEditor.setVisible(false);
            return;
        }
        templateFileEditor.setVisible(hasHtmlCsvTemplateOutput(outputType));
    }

    protected void setupVisibility(boolean customEnabled, ReportOutputType reportOutputType) {
        boolean templateOutputVisibility = hasTemplateOutput(reportOutputType);
        boolean enabled = templateOutputVisibility && customEnabled;
        boolean groovyScriptVisibility = enabled && hasScriptCustomDefinedBy(getEditedEntity().getCustomDefinedBy());

        customField.setVisible(templateOutputVisibility);
        customDefinedByField.setVisible(enabled);
        customDefinitionField.setVisible(enabled);

        setCustomDefinitionIconsVisible(groovyScriptVisibility);

        customDefinedByField.setRequired(enabled);
        customDefinitionField.setRequired(enabled);

        boolean supportAlterableForTemplate = templateOutputVisibility && !enabled;
        alterableField.setVisible(supportAlterableForTemplate);

        templateUploadField.setVisible(templateOutputVisibility);
        outputNamePatternField.setVisible(templateOutputVisibility);

        setupTemplateTypeVisibility(templateOutputVisibility);
        setupVisibilityDescriptionEdit(enabled, reportOutputType);
    }

    protected void setupTemplateTypeVisibility(boolean visibility) {
        String extension = "";
        if (getEditedEntity().getDocumentName() != null) {
            extension = FilenameUtils.getExtension(getEditedEntity().getDocumentName()).toUpperCase();
        }
        isGroovyRadioButtonGroup.setVisible(visibility
                && ReportOutputType.HTML.equals(ReportOutputType.getTypeFromExtension(extension)));
    }

    protected void setupVisibilityDescriptionEdit(boolean customEnabled, ReportOutputType reportOutputType) {
        AbstractDescriptionEditFragment<?> applicableEditor =
                getDescriptionEditors().stream()
                        .filter(c -> c.isApplicable(reportOutputType))
                        .findFirst().orElse(null);
        if (applicableEditor != null) {
            descriptionEditBox.setVisible(!customEnabled);
            applicableEditor.setVisible(!customEnabled);
            applicableEditor.setReportTemplate(getEditedEntity());

            if (!customEnabled && applicableEditor.isSupportPreview()) {
                applicableEditor.showPreview();
            } else {
                applicableEditor.hidePreview();
            }
        }

        for (AbstractDescriptionEditFragment<?> editor : getDescriptionEditors()) {
            if (applicableEditor != editor) {
                editor.setVisible(false);
            }
            if (applicableEditor == null) {
                editor.hidePreview();
                descriptionEditBox.setVisible(false);
            }
        }
    }

    protected boolean validateTemplateFile() {
        ReportTemplate template = getEditedEntity();
        if (!Boolean.TRUE.equals(template.getCustom())
                && hasTemplateOutput(template.getReportOutputType())
                && template.getContent() == null) {
            StringBuilder notification = new StringBuilder(
                    messageBundle.getMessage("validation.template.uploadTemplate"));

            if (StringUtils.isEmpty(template.getCode())) {
                notification.append("\n").append(messageBundle.getMessage("validation.template.specifyCode"));
            }
            if (template.getOutputType() == null) {
                notification.append("\n").append(messageBundle.getMessage("validation.template.specifyOutputType"));
            }
            notifications.create(messageBundle.getMessage("notification.template.validation.header"),
                            notification.toString())
                    .withPosition(Notification.Position.BOTTOM_END)
                    .show();
            return false;
        }
        return true;
    }

    protected boolean validateInputOutputFormats() {
        ReportTemplate reportTemplate = getEditedEntity();
        String name = reportTemplate.getName();
        if (!Boolean.TRUE.equals(reportTemplate.getCustom())
                && hasTemplateOutput(reportTemplate.getReportOutputType())
                && name != null) {
            String inputType = name.contains(".") ? name.substring(name.lastIndexOf(".") + 1) : "";

            ReportOutputType outputTypeValue = outputTypeField.getValue();
            if (!ReportPrintHelper.getInputOutputTypesMapping().containsKey(inputType) ||
                    !ReportPrintHelper.getInputOutputTypesMapping().get(inputType).contains(outputTypeValue)) {
                notifications.create(messageBundle.getMessage("notification.inputOutputTypesIncompatible.header"))
                        .withPosition(Notification.Position.BOTTOM_END)
                        .show();
                return false;
            }
        }
        return true;
    }

    protected void updateOutputType() {
        if (outputTypeField.getValue() == null) {
            String extension = FilenameUtils.getExtension(templateUploadField.getFileName()).toUpperCase();
            ReportOutputType reportOutputType = ReportOutputType.getTypeFromExtension(extension);
            if (reportOutputType != null) {
                outputTypeField.setValue(reportOutputType);
            }
        }
    }

    protected boolean hasTemplateOutput(ReportOutputType reportOutputType) {
        return reportOutputType != ReportOutputType.CHART
                && reportOutputType != ReportOutputType.TABLE
                && reportOutputType != ReportOutputType.PIVOT_TABLE;
    }

    protected boolean hasScriptCustomDefinedBy(CustomTemplateDefinedBy customTemplateDefinedBy) {
        return CustomTemplateDefinedBy.SCRIPT == customTemplateDefinedBy;
    }

    protected boolean hasHtmlCsvTemplateOutput(@Nullable ReportOutputType reportOutputType) {
        return reportOutputType == ReportOutputType.CSV || reportOutputType == ReportOutputType.HTML;
    }

    protected boolean isContentAttributeUpdatePermitted(ReportTemplate reportTemplate) {
        return secureOperations.isEntityUpdatePermitted(metadata.getClass(reportTemplate), policyStore)
                && secureOperations.isEntityAttrUpdatePermitted(
                metadata.getClass(reportTemplate).getPropertyPath("content"), policyStore);
    }
}