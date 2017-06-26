package org.dashbuilder.client.widgets.dataset.editor.workflow.edit;

import com.google.gwtmockito.GwtMockitoTestRunner;
import org.dashbuilder.client.widgets.dataset.editor.driver.ElasticSearchDataSetDefDriver;
import org.dashbuilder.client.widgets.dataset.editor.elasticsearch.ElasticSearchDataSetEditor;
import org.dashbuilder.client.widgets.dataset.editor.workflow.AbstractDataSetWorkflowTest;
import org.dashbuilder.client.widgets.dataset.editor.workflow.DataSetEditorWorkflow;
import org.dashbuilder.client.widgets.dataset.event.CancelRequestEvent;
import org.dashbuilder.client.widgets.dataset.event.SaveRequestEvent;
import org.dashbuilder.client.widgets.dataset.event.TestDataSetRequestEvent;
import org.dashbuilder.dataprovider.DataSetProviderType;
import org.dashbuilder.dataset.DataSet;
import org.dashbuilder.dataset.client.DataSetClientServices;
import org.dashbuilder.dataset.client.editor.DataSetDefRefreshAttributesEditor;
import org.dashbuilder.dataset.def.ElasticSearchDataSetDef;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.uberfire.mocks.EventSourceMock;

import static org.mockito.Mockito.*;

import javax.enterprise.inject.Instance;

@RunWith( GwtMockitoTestRunner.class )
public class ElasticSearchDataSetEditWorkflowTest extends AbstractDataSetWorkflowTest {

    @Mock
    EventSourceMock<SaveRequestEvent> saveRequestEvent;
    @Mock
    EventSourceMock<TestDataSetRequestEvent> testDataSetEvent;
    @Mock
    EventSourceMock<CancelRequestEvent> cancelRequestEvent;
    @Mock
    DataSetClientServices clientServices;
    @Mock
    DataSet dataSet;
    @Mock
    DataSetEditorWorkflow.View view;
    @Mock
    org.dashbuilder.client.widgets.dataset.editor.elasticsearch.ElasticSearchDataSetEditor elasticSearchEditor;
    @Mock
    ElasticSearchDataSetDef dataSetDef;
    @Mock
    DataSetDefRefreshAttributesEditor refreshEditor;
    @Mock
    Instance<ElasticSearchDataSetDefDriver> driverProvider;
    @Mock
    Instance<ElasticSearchDataSetEditor> editorProvider;

    private ElasticSearchDataSetEditWorkflow presenter;

    @Before
    public void setup() throws Exception {
        super.setup();
        presenter = new ElasticSearchDataSetEditWorkflow( clientServices,
                                                          validatorProvider,
                                                          saveRequestEvent,
                                                          testDataSetEvent,
                                                          cancelRequestEvent,
                                                          driverProvider,
                                                          editorProvider,
                                                          view );
        when( dataSetDef.getProvider() ).thenReturn( DataSetProviderType.ELASTICSEARCH );
        when( elasticSearchEditor.refreshEditor() ).thenReturn( refreshEditor );
        when( refreshEditor.isRefreshEnabled() ).thenReturn( true );
    }

    @Test
    public void testValidate() {
        presenter._setDataSetDef( dataSetDef );
        presenter.validate( true, true, true );
        verify( elasticSearchDataSetDefValidator, times( 1 ) ).validate( dataSetDef, true, true, true );
        verify( elasticSearchDataSetDefValidator, times( 0 ) ).validateCustomAttributes( dataSetDef, true );
    }

}
