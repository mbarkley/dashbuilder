package org.dashbuilder.client.widgets.dataset.editor.workflow;

import org.dashbuilder.client.widgets.dataset.editor.workflow.create.*;
import org.dashbuilder.client.widgets.dataset.editor.workflow.edit.*;
import org.dashbuilder.dataprovider.DataSetProviderType;
import org.jboss.errai.ioc.client.api.Disposer;
import org.jboss.errai.ioc.client.api.ManagedInstance;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;

/**
 * <p>Main entry point for editing or creating a data set definition instance.</p>
 * <p>It provides the workflow beans for each driver & editor.</p>
 * 
 * @since 0.4.0
 */
@Dependent
public class DataSetEditorWorkflowFactory {
    
    DataSetProviderTypeWorkflow providerTypeWorkflow;
    ManagedInstance<DataSetEditWorkflow<?,?>> dataSetEditWorkflowProvider;
    Disposer<DataSetEditorWorkflow<?>> disposer;
    private ManagedInstance<DataSetBasicAttributesWorkflow<?, ?>> basicAttributesWorkflowProvider;

    @Inject
    public DataSetEditorWorkflowFactory(final ManagedInstance<DataSetEditWorkflow<?,?>> dataSetEditWorkflowProvider,
                                        final Disposer<DataSetEditorWorkflow<?>> disposer,
                                        final ManagedInstance<DataSetBasicAttributesWorkflow<?, ?>> basicAttributesWorkflowProvider,
                                        final DataSetProviderTypeWorkflow providerTypeWorkflow) {
        this.dataSetEditWorkflowProvider = dataSetEditWorkflowProvider;
        this.disposer = disposer;
        this.basicAttributesWorkflowProvider = basicAttributesWorkflowProvider;
        this.providerTypeWorkflow = providerTypeWorkflow;
    }

    /**
     * Obtain the bean for editing a data set definition for a given type.
     * @param type The data set definition provider type to edit.
     * @return The workflow instance.
     */
    public DataSetEditWorkflow edit(final DataSetProviderType type) {
        final boolean isSQL = type != null && DataSetProviderType.SQL.equals(type);
        final boolean isBean = type != null && DataSetProviderType.BEAN.equals(type);
        final boolean isCSV = type != null && DataSetProviderType.CSV.equals(type);
        final boolean isEL = type != null && DataSetProviderType.ELASTICSEARCH.equals(type);
        Class<? extends DataSetEditWorkflow<?, ?>> workflowClass = null;
        if (isSQL) {
            workflowClass = SQLDataSetEditWorkflow.class;
        } else if (isCSV) {
            workflowClass = CSVDataSetEditWorkflow.class;
        } else if (isBean) {
            workflowClass = BeanDataSetEditWorkflow.class;
        } else if (isEL) {
            workflowClass = ElasticSearchDataSetEditWorkflow.class;
        }
        return  dataSetEditWorkflowProvider.select(workflowClass).get();
    }

    /**
     * Dispose the given workflow instance.
     */
    public void dispose(DataSetEditorWorkflow workflow) {
        workflow.dispose();
        disposer.dispose(workflow);
    }

    /**
     * Obtain the bean for editing the data set definition's provider type.
     * @return The workflow instance.
     */
    public DataSetProviderTypeWorkflow providerType() {
        return providerTypeWorkflow;
    }

    /**
     * Obtain the bean for creating (editing basic attributes to be able to perform a lookup)  a data set definition for a given type.
     * @param type The data set definition provider type to edit the basic attributes.
     * @return The workflow instance.
     */
    public DataSetBasicAttributesWorkflow basicAttributes(final DataSetProviderType type) {
        final boolean isSQL = type != null && DataSetProviderType.SQL.equals(type);
        final boolean isBean = type != null && DataSetProviderType.BEAN.equals(type);
        final boolean isCSV = type != null && DataSetProviderType.CSV.equals(type);
        final boolean isEL = type != null && DataSetProviderType.ELASTICSEARCH.equals(type);
        Class<? extends DataSetBasicAttributesWorkflow<?, ?>> workflowClass = null;
        if (isSQL) {
            workflowClass = SQLDataSetBasicAttributesWorkflow.class;
        } else if (isCSV) {
            workflowClass = CSVDataSetBasicAttributesWorkflow.class;
        } else if (isBean) {
            workflowClass = BeanDataSetBasicAttributesWorkflow.class;
        } else if (isEL) {
            workflowClass = ElasticSearchDataSetBasicAttributesWorkflow.class;
        }
        return basicAttributesWorkflowProvider.select(workflowClass).get();
    }
    
}
