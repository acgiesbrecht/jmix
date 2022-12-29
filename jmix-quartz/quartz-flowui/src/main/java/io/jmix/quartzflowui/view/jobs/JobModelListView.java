/*
 * Copyright 2022 Haulmont.
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

package io.jmix.quartzflowui.view.jobs;

import com.google.common.base.Strings;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.util.RemoveOperation;
import io.jmix.flowui.view.*;
import io.jmix.quartz.model.JobModel;
import io.jmix.quartz.model.JobSource;
import io.jmix.quartz.model.JobState;
import io.jmix.quartz.service.QuartzService;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.model.CollectionContainer;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Comparator.*;

@Route(value = "quartz/jobmodels", layout = DefaultMainViewParent.class)
@ViewController("quartz_JobModel.list")
@ViewDescriptor("job-model-list-view.xml")
@LookupComponent("jobModelsTable")
public class JobModelListView extends StandardListView<JobModel> {

    @ViewComponent
    private RemoveOperation removeOperation;

    @ViewComponent
    private CollectionContainer<JobModel> jobModelsDc;

    @ViewComponent
    private DataGrid<JobModel> jobModelsTable;

    @ViewComponent
    private TextField nameField;

    @ViewComponent
    private TextField classField;

    @ViewComponent
    private TextField groupField;

    @ViewComponent
    private ComboBox<JobState> jobStateComboBox;

    @Autowired
    private QuartzService quartzService;

    @Autowired
    private Notifications notifications;

    @Autowired
    private MessageBundle messageBundle;

    @Subscribe
    public void onBeforeShow(View.BeforeShowEvent event) {
        jobStateComboBox.setItems(JobState.values());
        loadJobsData();
    }

    private void loadJobsData() {
        List<JobModel> sortedJobs = quartzService.getAllJobs().stream()
                .filter(jobModel -> Strings.isNullOrEmpty(nameField.getValue()) || StringUtils.containsIgnoreCase(jobModel.getJobName(), nameField.getValue()))
                .filter(jobModel -> Strings.isNullOrEmpty(classField.getValue()) || StringUtils.containsIgnoreCase(jobModel.getJobName(), classField.getValue()))
                .filter(jobModel -> Strings.isNullOrEmpty(groupField.getValue()) || StringUtils.containsIgnoreCase(jobModel.getJobGroup(), groupField.getValue()))
                .filter(jobModel -> jobStateComboBox.getValue() == null || jobStateComboBox.getValue().equals(jobModel.getJobState()))
                .sorted(comparing(JobModel::getJobState, nullsLast(naturalOrder()))
                        .thenComparing(JobModel::getJobName))
                .collect(Collectors.toList());

        jobModelsDc.setItems(sortedJobs);
    }

    @Install(to = "jobModelsTable.executeNow", subject = "enabledRule")
    private boolean jobModelsTableExecuteNowEnabledRule() {
        return !CollectionUtils.isEmpty(jobModelsTable.getSelectedItems())
                && !isJobActive(jobModelsTable.getSingleSelectedItem());
    }

    @Install(to = "jobModelsTable.activate", subject = "enabledRule")
    private boolean jobModelsTableActivateEnabledRule() {
        if (CollectionUtils.isEmpty(jobModelsTable.getSelectedItems())) {
            return false;
        }

        JobModel selectedJobModel = jobModelsTable.getSingleSelectedItem();
        return !isJobActive(selectedJobModel) && CollectionUtils.isNotEmpty(selectedJobModel.getTriggers());
    }

    @Install(to = "jobModelsTable.deactivate", subject = "enabledRule")
    private boolean jobModelsTableDeactivateEnabledRule() {
        return !CollectionUtils.isEmpty(jobModelsTable.getSelectedItems())
                && isJobActive(jobModelsTable.getSingleSelectedItem());
    }

    @Install(to = "jobModelsTable.remove", subject = "enabledRule")
    private boolean jobModelsTableRemoveEnabledRule() {
        if (CollectionUtils.isEmpty(jobModelsTable.getSelectedItems())) {
            return false;
        }

        JobModel selectedJobModel = jobModelsTable.getSingleSelectedItem();
        return !isJobActive(selectedJobModel) && JobSource.USER_DEFINED.equals(selectedJobModel.getJobSource());
    }

    @Subscribe("jobModelsTable.executeNow")
    public void onJobModelsTableExecuteNow(ActionPerformedEvent event) {
        JobModel selectedJobModel = jobModelsTable.getSingleSelectedItem();
        quartzService.executeNow(selectedJobModel.getJobName(), selectedJobModel.getJobGroup());
        notifications.create(messageBundle.formatMessage("jobExecuted", selectedJobModel.getJobName()))
                .withType(Notifications.Type.DEFAULT)
                .show();
        loadJobsData();
    }

    @Subscribe("jobModelsTable.activate")
    public void onJobModelsTableActivate(ActionPerformedEvent event) {
        JobModel selectedJobModel = jobModelsTable.getSingleSelectedItem();
        quartzService.resumeJob(selectedJobModel.getJobName(), selectedJobModel.getJobGroup());
        notifications.create(messageBundle.formatMessage("jobResumed", selectedJobModel.getJobName()))
                .withType(Notifications.Type.DEFAULT)
                .show();

        loadJobsData();
    }

    @Subscribe("jobModelsTable.deactivate")
    public void onJobModelsTableDeactivate(ActionPerformedEvent event) {
        JobModel selectedJobModel = jobModelsTable.getSingleSelectedItem();
        quartzService.pauseJob(selectedJobModel.getJobName(), selectedJobModel.getJobGroup());
        notifications.create(messageBundle.formatMessage("jobPaused", selectedJobModel.getJobName()))
                .withType(Notifications.Type.DEFAULT)
                .show();

        loadJobsData();
    }

    @Subscribe("jobModelsTable.remove")
    public void onJobModelsTableRemove(ActionPerformedEvent event) {
        removeOperation.builder(jobModelsTable)
                .withConfirmation(true)
                .beforeActionPerformed(e -> {
                    if (CollectionUtils.isNotEmpty(e.getItems())) {
                        JobModel jobToDelete = e.getItems().get(0);
                        quartzService.deleteJob(jobToDelete.getJobName(), jobToDelete.getJobGroup());
                        notifications.create(messageBundle.formatMessage("jobDeleted", jobToDelete.getJobName()))
                                .withType(Notifications.Type.DEFAULT)
                                .show();
                        loadJobsData();
                    }
                })
                .remove();
    }

    @Subscribe("jobModelsTable.refresh")
    public void onJobModelsTableRefresh(ActionPerformedEvent event) {
        loadJobsData();
    }

    @Install(to = "jobModelsTable.create", subject = "afterSaveHandler")
    private void jobModelsTableCreateAfterCommitHandler(JobModel jobModel) {
        loadJobsData();
    }

    @Install(to = "jobModelsTable.edit", subject = "afterSaveHandler")
    private void jobModelsTableEditAfterCommitHandler(JobModel jobModel) {
        loadJobsData();
    }

    private boolean isJobActive(JobModel jobModel) {
        return jobModel != null && jobModel.getJobState() == JobState.NORMAL;
    }

    @Subscribe("applyFilter")
    public void onApplyFilter(ActionPerformedEvent event) {
        loadJobsData();
    }

}