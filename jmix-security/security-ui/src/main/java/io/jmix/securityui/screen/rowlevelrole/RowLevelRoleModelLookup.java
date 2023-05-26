/*
 * Copyright 2020 Haulmont.
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

package io.jmix.securityui.screen.rowlevelrole;

import io.jmix.security.role.RowLevelRoleRepository;
import io.jmix.securityui.model.ResourceRoleModel;
import io.jmix.securityui.model.RoleModelConverter;
import io.jmix.securityui.model.RowLevelRoleModel;
import io.jmix.securityui.screen.rolefilter.RoleFilter;
import io.jmix.securityui.screen.rolefilter.RoleFilterFragment;
import io.jmix.ui.model.CollectionContainer;
import io.jmix.ui.screen.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@UiController("sec_RowLevelRoleModel.lookup")
@UiDescriptor("row-level-role-model-lookup.xml")
@LookupComponent("roleModelsTable")
public class RowLevelRoleModelLookup extends StandardLookup<ResourceRoleModel> {

    @Autowired
    private CollectionContainer<RowLevelRoleModel> roleModelsDc;

    @Autowired
    private RowLevelRoleRepository roleRepository;

    @Autowired
    private RoleModelConverter roleModelConverter;

    @Autowired
    private RoleFilterFragment filterFragment;

    private List<String> excludedRolesCodes = Collections.emptyList();

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        loadRoles(new RoleFilter());

        filterFragment.setSourceFieldVisible(false);
        filterFragment.setChangeListener(this::loadRoles);
    }

    private void loadRoles(RoleFilter roleFilter) {
        List<RowLevelRoleModel> roleModels = roleRepository.getAllRoles().stream()
                .filter(role -> roleFilter.matches(role) && !excludedRolesCodes.contains(role.getCode()))
                .map(roleModelConverter::createRowLevelRoleModel)
                .sorted(Comparator.comparing(RowLevelRoleModel::getName))
                .collect(Collectors.toList());
        roleModelsDc.setItems(roleModels);
    }

    public void setExcludedRolesCodes(List<String> excludedRolesCodes) {
        this.excludedRolesCodes = excludedRolesCodes;
    }
}