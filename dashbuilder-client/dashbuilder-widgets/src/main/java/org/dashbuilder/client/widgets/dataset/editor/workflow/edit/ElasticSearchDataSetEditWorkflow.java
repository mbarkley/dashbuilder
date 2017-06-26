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
package org.dashbuilder.client.widgets.dataset.editor.workflow.edit;

import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;

import org.dashbuilder.client.widgets.dataset.editor.driver.ElasticSearchDataSetDefDriver;
import org.dashbuilder.client.widgets.dataset.editor.elasticsearch.ElasticSearchDataSetEditor;
import org.dashbuilder.client.widgets.dataset.event.CancelRequestEvent;
import org.dashbuilder.client.widgets.dataset.event.SaveRequestEvent;
import org.dashbuilder.client.widgets.dataset.event.TestDataSetRequestEvent;
import org.dashbuilder.dataset.client.DataSetClientServices;
import org.dashbuilder.dataset.client.editor.ElasticSearchDataSetDefEditor;
import org.dashbuilder.dataset.def.ElasticSearchDataSetDef;
import org.dashbuilder.validations.DataSetValidatorProvider;


/**
 * <p>Elastic Search Data Set Editor workflow presenter for editing a data set definition instance.</p>
 * <p>This class is the main entry point for editing an existing data set definition instance. It links the gwt editors with the given driver to perform flushing and validations.</p>
 *
 * @since 0.4.0
 */
@Dependent
public class ElasticSearchDataSetEditWorkflow extends DataSetEditWorkflow<ElasticSearchDataSetDef, ElasticSearchDataSetDefEditor> {

    @Inject
    public ElasticSearchDataSetEditWorkflow(final DataSetClientServices clientServices,
                                   final DataSetValidatorProvider validatorProvider,
                                   final Event<SaveRequestEvent> saveRequestEvent,
                                   final Event<TestDataSetRequestEvent> testDataSetEvent,
                                   final Event<CancelRequestEvent> cancelRequestEvent,
                                   final Instance<ElasticSearchDataSetDefDriver> driverProvider,
                                   final Instance<ElasticSearchDataSetEditor> editorProvider,
                                   final View view) {
        super(clientServices,
              validatorProvider,
              saveRequestEvent,
              testDataSetEvent,
              cancelRequestEvent,
              driverProvider,
              editorProvider,
              view);
    }
}
