/*
 * Copyright 2014 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dashbuilder.client.widgets.dataset.editor.workflow.create;

import javax.annotation.PostConstruct;
import javax.enterprise.event.Event;
import javax.inject.Provider;
import javax.validation.ConstraintViolation;

import com.google.gwt.editor.client.Editor;
import com.google.gwt.editor.client.SimpleBeanEditorDriver;
import com.google.gwt.user.client.ui.IsWidget;
import org.dashbuilder.client.widgets.dataset.editor.attributes.DataSetDefBasicAttributesEditor;
import org.dashbuilder.client.widgets.dataset.editor.driver.DataSetDefBasicAttributesDriver;
import org.dashbuilder.client.widgets.dataset.editor.workflow.DataSetEditorWorkflow;
import org.dashbuilder.client.widgets.dataset.event.CancelRequestEvent;
import org.dashbuilder.client.widgets.dataset.event.SaveRequestEvent;
import org.dashbuilder.client.widgets.dataset.event.TestDataSetRequestEvent;
import org.dashbuilder.dataset.client.DataSetClientServices;
import org.dashbuilder.dataset.def.DataSetDef;
import org.dashbuilder.validations.DataSetValidatorProvider;
import org.uberfire.mvp.Command;


/**
 * <p>Data Set Editor workflow presenter for creating a data set definition instance.</p>
 * <p>GWT editors and drivers must be type safe as they're generated during the deferred binding at compile time, so this class must be extended using concretes types for each driver & editor.</p>
 *
 * @since 0.4.0
 */
public abstract class DataSetBasicAttributesWorkflow<T extends DataSetDef, E extends Editor<? super T>> extends DataSetEditorWorkflow<T> {

    DataSetDefBasicAttributesEditor basicAttributesEditor;
    DataSetDefBasicAttributesDriver dataSetDefBasicAttributesDriver;

    SimpleBeanEditorDriver<T, E> driver;
    E editor;
    Provider<DataSetDefBasicAttributesDriver> basicAttributesDriverProvider;
    private Provider<? extends SimpleBeanEditorDriver<T, E>> driverProvider;
    private Provider<? extends E> editorProvider;

    public DataSetBasicAttributesWorkflow(final DataSetClientServices clientServices,
                                          final DataSetValidatorProvider validatorProvider,
                                          final DataSetDefBasicAttributesEditor basicAttributesEditor,
                                          final Event<SaveRequestEvent> saveRequestEvent,
                                          final Event<TestDataSetRequestEvent> testDataSetEvent,
                                          final Event<CancelRequestEvent> cancelRequestEvent,
                                          final Provider<DataSetDefBasicAttributesDriver> basicAttributesDriverProvider,
                                          final Provider<? extends SimpleBeanEditorDriver<T, E>> driverProvider,
                                          final Provider<? extends E> editorProvider,
                                          final View view) {

        super(clientServices, validatorProvider, saveRequestEvent, testDataSetEvent, cancelRequestEvent, view);
        this.basicAttributesEditor = basicAttributesEditor;
        this.basicAttributesDriverProvider = basicAttributesDriverProvider;
        this.driverProvider = driverProvider;
        this.editorProvider = editorProvider;
    }

    @PostConstruct
    public void init() {
        super.init();
    }

    protected Iterable<ConstraintViolation<?>> validate() {
        return validatorProvider.validateAttributes( getDataSetDef() );
    }

    public DataSetBasicAttributesWorkflow edit(final T def) {
        checkDataSetDefNotNull(def);

        clear();
        this.dataSetDef = def;
        return this;
    }

    public DataSetEditorWorkflow basicAttributesEdition() {
        checkDataSetDefNotNull();

        dataSetDefBasicAttributesDriver = basicAttributesDriverProvider.get();
        dataSetDefBasicAttributesDriver.initialize(basicAttributesEditor);
        dataSetDefBasicAttributesDriver.edit(getDataSetDef());

        driver = driverProvider.get();
        editor = editorProvider.get();
        driver.initialize(editor);
        driver.edit(getDataSetDef());

        this.flushCommand = new Command() {
            @Override
            public void execute() {
                flush(dataSetDefBasicAttributesDriver);
                flush(driver);
            }
        };

        this.stepValidator = new Command() {
            @Override
            public void execute() {

                // Data set definition basic attributes validation.
                Iterable<ConstraintViolation<?>> basicAttsViolations = validatorProvider.validateBasicAttributes(getDataSetDef());
                dataSetDefBasicAttributesDriver.setConstraintViolations(basicAttsViolations);
                addViolations(basicAttsViolations);

                Iterable<ConstraintViolation<?>> violations = validate();
                driver.setConstraintViolations(violations);
                addViolations(violations);
            }
        };

        // Show the view.
        view.clearView();
        view.add(basicAttributesEditor.asWidget());
        view.add(((IsWidget) editor));

        return this;
    }

    /**
     * For unit tests use cases.
     */
    void _setDataSetDef(final T def) {
        this.dataSetDef = def;
    }

}
