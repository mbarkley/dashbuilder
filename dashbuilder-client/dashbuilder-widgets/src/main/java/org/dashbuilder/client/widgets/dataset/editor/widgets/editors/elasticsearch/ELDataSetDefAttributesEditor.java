/**
 * Copyright (C) 2014 JBoss Inc
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
package org.dashbuilder.client.widgets.dataset.editor.widgets.editors.elasticsearch;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import javax.enterprise.context.Dependent;
import javax.validation.ConstraintViolation;

import com.google.gwt.core.client.GWT;
import com.google.gwt.editor.client.EditorError;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.validation.client.impl.ConstraintViolationImpl;
import com.google.gwt.validation.client.impl.PathImpl;
import org.dashbuilder.client.widgets.dataset.editor.widgets.editors.AbstractDataSetDefEditor;
import org.dashbuilder.common.client.validation.editors.ValueBoxEditorDecorator;
import org.dashbuilder.dataset.client.validation.editors.ELDataSetDefEditor;
import org.dashbuilder.dataset.def.ElasticSearchDataSetDef;
import org.dashbuilder.dataset.validation.DataSetValidationMessages;

/**
 * <p>This is the view implementation for Data Set Editor widget for editing ElasticSearch provider specific attributes.</p>
 * 
 * @since 0.3.0 
 */
@Dependent
public class ELDataSetDefAttributesEditor extends AbstractDataSetDefEditor implements ELDataSetDefEditor {
    
    interface ELDataSetDefAttributesEditorBinder extends UiBinder<Widget, ELDataSetDefAttributesEditor> {}
    private static ELDataSetDefAttributesEditorBinder uiBinder = GWT.create(ELDataSetDefAttributesEditorBinder.class);

    @UiField
    FlowPanel elAttributesPanel;

    @UiField
    ValueBoxEditorDecorator<String> serverURL;

    @UiField
    ValueBoxEditorDecorator<String> clusterName;

    @UiField
    ValueBoxEditorDecorator<String> index;

    @UiField
    ValueBoxEditorDecorator<String> type;

    private boolean isEditMode;

    public ELDataSetDefAttributesEditor() {
        initWidget(uiBinder.createAndBindUi(this));
    }

    @Override
    public Collection<ConstraintViolation<?>> getViolations() {
        if (super.getViolations() == null) this.violations = new LinkedList<ConstraintViolation<?>>();
        return (Collection<ConstraintViolation<?>>) this.violations;
    }

    public boolean isEditMode() {
        return isEditMode;
    }

    public void setEditMode(boolean isEditMode) {
        this.isEditMode = isEditMode;
    }

    @Override
    public void showErrors(List<EditorError> errors) {
        consumeErrors(errors);
    }

    public void clear() {
        super.clear();
        serverURL.clear();
        clusterName.clear();
        index.clear();
        type.clear();
    }
}
