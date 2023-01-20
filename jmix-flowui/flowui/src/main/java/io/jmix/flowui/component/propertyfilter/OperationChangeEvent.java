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

package io.jmix.flowui.component.propertyfilter;

import com.vaadin.flow.component.ComponentEvent;

public class OperationChangeEvent<V> extends ComponentEvent<PropertyFilter<V>> {

    protected final FilteringOperation newOperation;
    protected final FilteringOperation prevOperation;

    public OperationChangeEvent(PropertyFilter source,
                                FilteringOperation newOperation, FilteringOperation prevOperation,
                                boolean fromClient) {
        super(source, fromClient);
        this.prevOperation = prevOperation;
        this.newOperation = newOperation;
    }

    /**
     * @return new operation value
     */
    public FilteringOperation getNewOperation() {
        return newOperation;
    }

    /**
     * @return previous operation value
     */
    public FilteringOperation getPreviousOperation() {
        return prevOperation;
    }
}
