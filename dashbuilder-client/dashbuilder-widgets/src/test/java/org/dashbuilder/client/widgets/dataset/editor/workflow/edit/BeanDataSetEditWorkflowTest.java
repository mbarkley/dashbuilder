package org.dashbuilder.client.widgets.dataset.editor.workflow.edit;

import com.google.gwtmockito.GwtMockitoTestRunner;
import org.dashbuilder.client.widgets.dataset.editor.driver.BeanDataSetDefDriver;
import org.dashbuilder.client.widgets.dataset.editor.workflow.AbstractDataSetWorkflowTest;
import org.dashbuilder.client.widgets.dataset.editor.workflow.DataSetEditorWorkflow;
import org.dashbuilder.client.widgets.dataset.event.CancelRequestEvent;
import org.dashbuilder.client.widgets.dataset.event.SaveRequestEvent;
import org.dashbuilder.client.widgets.dataset.event.TestDataSetRequestEvent;
import org.dashbuilder.dataprovider.DataSetProviderType;
import org.dashbuilder.dataset.DataSet;
import org.dashbuilder.dataset.client.DataSetClientServices;
import org.dashbuilder.dataset.client.editor.DataSetDefRefreshAttributesEditor;
import org.dashbuilder.dataset.def.BeanDataSetDef;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.uberfire.mocks.EventSourceMock;

import static org.mockito.Mockito.*;

import javax.enterprise.inject.Instance;

@RunWith( GwtMockitoTestRunner.class )
public class BeanDataSetEditWorkflowTest extends AbstractDataSetWorkflowTest {

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
    org.dashbuilder.client.widgets.dataset.editor.bean.BeanDataSetEditor beanEditor;
    @Mock
    BeanDataSetDef dataSetDef;
    @Mock
    DataSetDefRefreshAttributesEditor refreshEditor;
    @Mock
    Instance<BeanDataSetDefDriver> driverProvider;
    @Mock
    Instance<org.dashbuilder.client.widgets.dataset.editor.bean.BeanDataSetEditor> editorProvider;

    private BeanDataSetEditWorkflow presenter;

    @Before
    public void setup() throws Exception {
        super.setup();

        presenter = new BeanDataSetEditWorkflow(clientServices,
                                                validatorProvider,
                                                saveRequestEvent,
                                                testDataSetEvent,
                                                cancelRequestEvent,
                                                driverProvider,
                                                editorProvider,
                                                view);
        when( dataSetDef.getProvider() ).thenReturn( DataSetProviderType.BEAN );
        when( beanEditor.refreshEditor() ).thenReturn( refreshEditor );
        when( refreshEditor.isRefreshEnabled() ).thenReturn( true );
    }

    @Test
    public void testValidate() {
        presenter._setDataSetDef( dataSetDef );
        presenter.validate( true, true, true );
        verify( beanDataSetDefValidator, times( 1 ) ).validate( dataSetDef, true, true, true );
        verify( beanDataSetDefValidator, times( 0 ) ).validateCustomAttributes( dataSetDef, true );
    }

}
