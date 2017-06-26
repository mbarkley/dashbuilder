package org.dashbuilder.client.widgets.dataset.editor.workflow.create;

import javax.inject.Provider;
import javax.validation.ConstraintViolation;

import com.google.gwt.user.client.ui.IsWidget;
import org.dashbuilder.client.widgets.dataset.editor.attributes.DataSetDefBasicAttributesEditor;
import org.dashbuilder.client.widgets.dataset.editor.driver.DataSetDefBasicAttributesDriver;
import org.dashbuilder.client.widgets.dataset.editor.workflow.AbstractDataSetWorkflowTest;
import org.dashbuilder.client.widgets.dataset.editor.workflow.DataSetEditorWorkflow;
import org.dashbuilder.client.widgets.dataset.event.CancelRequestEvent;
import org.dashbuilder.client.widgets.dataset.event.SaveRequestEvent;
import org.dashbuilder.client.widgets.dataset.event.TestDataSetRequestEvent;
import org.dashbuilder.dataset.client.DataSetClientServices;
import org.dashbuilder.dataset.def.DataSetDef;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.uberfire.mocks.EventSourceMock;
import org.uberfire.mvp.Command;

import static org.mockito.Mockito.*;

@RunWith( MockitoJUnitRunner.class )
public class DataSetBasicAttributesWorkflowTest extends AbstractDataSetWorkflowTest {

    @Mock
    EventSourceMock<SaveRequestEvent> saveRequestEvent;
    @Mock
    EventSourceMock<TestDataSetRequestEvent> testDataSetEvent;
    @Mock
    EventSourceMock<CancelRequestEvent> cancelRequestEvent;
    @Mock
    DataSetClientServices clientServices;
    @Mock
    DataSetDefBasicAttributesEditor basicAttributesEditor;
    @Mock
    DataSetDefBasicAttributesDriver dataSetDefBasicAttributesDriver;
    @Mock
    DataSetEditorWorkflow.View view;
    @Mock
    Provider<DataSetDefBasicAttributesDriver> basicAttributesDriverProvider;
    @Mock
    Provider<DataSetDefBasicAttributesEditor> editorProvider;
    private DataSetBasicAttributesWorkflow presenter;

    @Before
    public void setup() throws Exception {
        super.setup();

        // Bean instantiation mocks.
        when(basicAttributesDriverProvider.get()).then(inv -> dataSetDefBasicAttributesDriver);
        when(editorProvider.get()).then(inv -> basicAttributesEditor);

        presenter = new DataSetBasicAttributesWorkflow( clientServices,
                                                        validatorProvider,
                                                        basicAttributesEditor,
                                                        saveRequestEvent,
                                                        testDataSetEvent,
                                                        cancelRequestEvent,
                                                        basicAttributesDriverProvider,
                                                        basicAttributesDriverProvider,
                                                        editorProvider,
                                                        view ) {

            @Override
            protected Iterable<ConstraintViolation<?>> validate() {
                return null;
            }
        };

    }

    @Test
    public void testBasicAttributesEdition() {
        DataSetDef def = mock( DataSetDef.class );
        presenter.edit( def ).basicAttributesEdition();
        verify( basicAttributesDriverProvider, times( 2 ) ).get();
        verify( dataSetDefBasicAttributesDriver, times( 2 ) ).initialize( basicAttributesEditor );
        verify( dataSetDefBasicAttributesDriver, times( 2 ) ).edit( def );
        verify( view, times( 2 ) ).clearView();
        verify( view, times( 2 ) ).add( any( IsWidget.class ) );
        verify( view, times( 0 ) ).init( presenter );
        verify( view, times( 0 ) ).addButton( anyString(), anyString(), anyBoolean(), any( Command.class ) );
        verify( view, times( 0 ) ).clearButtons();
    }

}
