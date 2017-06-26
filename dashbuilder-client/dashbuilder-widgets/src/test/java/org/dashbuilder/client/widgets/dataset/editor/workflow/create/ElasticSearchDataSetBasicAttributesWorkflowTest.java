package org.dashbuilder.client.widgets.dataset.editor.workflow.create;

import com.google.gwtmockito.GwtMockitoTestRunner;
import org.dashbuilder.client.widgets.dataset.editor.attributes.DataSetDefBasicAttributesEditor;
import org.dashbuilder.client.widgets.dataset.editor.driver.DataSetDefBasicAttributesDriver;
import org.dashbuilder.client.widgets.dataset.editor.driver.ElasticSearchDataSetDefAttributesDriver;
import org.dashbuilder.client.widgets.dataset.editor.workflow.AbstractDataSetWorkflowTest;
import org.dashbuilder.client.widgets.dataset.editor.workflow.DataSetEditorWorkflow;
import org.dashbuilder.client.widgets.dataset.event.CancelRequestEvent;
import org.dashbuilder.client.widgets.dataset.event.SaveRequestEvent;
import org.dashbuilder.client.widgets.dataset.event.TestDataSetRequestEvent;
import org.dashbuilder.dataprovider.DataSetProviderType;
import org.dashbuilder.dataset.client.DataSetClientServices;
import org.dashbuilder.dataset.def.ElasticSearchDataSetDef;
import org.jboss.errai.ioc.client.api.ManagedInstance;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.uberfire.mocks.EventSourceMock;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.*;

@RunWith( GwtMockitoTestRunner.class )
public class ElasticSearchDataSetBasicAttributesWorkflowTest extends AbstractDataSetWorkflowTest {

    @Mock
    EventSourceMock<SaveRequestEvent> saveRequestEvent;
    @Mock
    EventSourceMock<TestDataSetRequestEvent> testDataSetEvent;
    @Mock
    EventSourceMock<CancelRequestEvent> cancelRequestEvent;
    @Mock
    DataSetClientServices clientServices;
    @Mock
    ElasticSearchDataSetDef dataSetDef;
    @Mock
    DataSetDefBasicAttributesEditor basicAttributesEditor;
    @Mock
    org.dashbuilder.client.widgets.dataset.editor.elasticsearch.ElasticSearchDataSetDefAttributesEditor elDataSetDefAttributesEditor;
    @Mock
    DataSetEditorWorkflow.View view;
    ManagedInstance<DataSetDefBasicAttributesDriver> basicAttributesDriverProvider;
    ManagedInstance<org.dashbuilder.client.widgets.dataset.editor.elasticsearch.ElasticSearchDataSetDefAttributesEditor> editorProvider;
    ManagedInstance<ElasticSearchDataSetDefAttributesDriver> driverProvider;

    private ElasticSearchDataSetBasicAttributesWorkflow presenter;

    @Before
    public void setup() throws Exception {
        super.setup();
        presenter = new ElasticSearchDataSetBasicAttributesWorkflow( clientServices,
                                                                     validatorProvider,
                                                                     basicAttributesEditor,
                                                                     saveRequestEvent,
                                                                     testDataSetEvent,
                                                                     cancelRequestEvent,
                                                                     basicAttributesDriverProvider,
                                                                     editorProvider,
                                                                     driverProvider,
                                                                     view );
        when( dataSetDef.getProvider() ).thenReturn( DataSetProviderType.ELASTICSEARCH );
    }

    @Test
    public void testValidate() {
        presenter._setDataSetDef( dataSetDef );
        presenter.validate();
        verify( elasticSearchDataSetDefValidator, times( 1 ) ).validateCustomAttributes( dataSetDef );
        verify( elasticSearchDataSetDefValidator, times( 0 ) ).validate( any( ElasticSearchDataSetDef.class ),
                                                                         anyBoolean(),
                                                                         anyBoolean(),
                                                                         anyBoolean() );
    }

}
