/*
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.dashbuilder.client.menu.widgets;

import com.github.gwtbootstrap.client.ui.Button;
import com.github.gwtbootstrap.client.ui.*;
import com.github.gwtbootstrap.client.ui.RadioButton;
import com.github.gwtbootstrap.client.ui.TextBox;
import com.github.gwtbootstrap.client.ui.base.InlineLabel;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.*;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.*;
import org.dashbuilder.client.menu.MenuUtils;
import org.dashbuilder.client.resources.i18n.MenusConstants;
import org.dashbuilder.client.widgets.animations.AlphaAnimation;
import org.uberfire.client.mvp.PerspectiveActivity;
import org.uberfire.workbench.model.menu.*;
import org.uberfire.workbench.model.menu.MenuItem;

import javax.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The Menu Bar widget with editable capabilities for menu items.
 * @since 0.3.0
 */
@ApplicationScoped
public class MenuView extends Composite
        implements
        MenuScreen.View {

    private static final int ALPHA_ANIMATION_DURATION = 500;
    private static final String POINTS = "...";
    private static final String MENU_ITEM_TYPE_GROUP = "MENU_ITEM_TYPE_GROUP";
    private static final String DROPDOWN_OPEN_STYLE = "open";
    final MenuComponent.MenuItemTypes ITEM_TYPE_COMMAND = MenuComponent.MenuItemTypes.COMMAND;

    interface MenuViewBinder
            extends
            UiBinder<Panel, MenuView> {

    }

    private static MenuViewBinder uiBinder = GWT.create( MenuViewBinder.class );

    interface MenuViewStyle extends CssResource {
        String mainPanel();
        String labelError();
        String removeButtonPanelDragOver();
        String menuItemDropOver_b();
        String menuItemDropOver_l();
        String menuItemDropOver_r();
        String menuItemTypeRadioButton();
    }

    @UiField
    public Nav menuBarLeft;

    @UiField
    public Nav menuBarCenter;

    @UiField
    public Nav menuBarRight;

    @UiField
    MenuViewStyle style;
    
    @UiField
    NavForm barForm;
    
    @UiField
    Button addButton;
    
    @UiField
    FlowPanel removeButtonPanel;
    
    @UiField
    Button removeButton;
    
    @UiField
    Modal menuItemModalPanel;
    
    @UiField
    WellForm menuItemForm;
    
    @UiField
    InlineLabel nameLabel;
    
    @UiField
    TextBox menuItemName;

    @UiField
    FlowPanel perspectivesPanel;
    
    @UiField
    InlineLabel perspectiveLabel;

    @UiField
    InlineLabel menuItemTypeLabel;
    
    @UiField
    FlowPanel menuItemTypesPanel;
    
    @UiField
    DropdownButton menuItemPerspectives;
    
    @UiField
    Button menuItemOkButton;

    private boolean isEdit;
    private PerspectiveActivity selectedPerspective;
    private MenuComponent.MenuItemTypes selectedItemType;
    private MenuScreen.ViewCallback viewCallback;
    private Timer menuGroupTimer;
    
    public MenuView() {
        initWidget( uiBinder.createAndBindUi( this ) );
        enableEdition();
        isEdit = false;
        barForm.getTextBox().setVisible(false);
        
        // Enable remove button drop feature.
        disableRemove();
        removeButtonPanel.addDomHandler(new DragOverHandler() {
            @Override
            public void onDragOver(final DragOverEvent event) {
                if (isEdit) removeButtonPanel.addStyleName(style.removeButtonPanelDragOver());
            }
        }, DragOverEvent.getType());
        removeButtonPanel.addDomHandler(new DragLeaveHandler() {
            @Override
            public void onDragLeave(final DragLeaveEvent event) {
                if (isEdit) removeButtonPanel.removeStyleName(style.removeButtonPanelDragOver());
            }
        }, DragLeaveEvent.getType());
        removeButtonPanel.addDomHandler(new DropHandler() {
            @Override
            public void onDrop(final DropEvent event) {
                if (isEdit) {
                    removeButtonPanel.removeStyleName(style.removeButtonPanelDragOver());
                    final String id = event.getData("text");
                    if (id != null) {
                        viewCallback.removeItem(id);

                    }    
                }
            }
        }, DropEvent.getType());
    }

    @Override
    public void build(final Menus menus, final MenuScreen.ViewCallback callback) {
        this.viewCallback = callback;
        clearMenuBars();
        buildMenuItems(menus);
        disableRemove();
    }

    @UiHandler(value = "addButton")
    public void onAdd(final ClickEvent clickEvent) {
        showMenuItemModalPanel();
    }

    @UiHandler(value = "menuItemOkButton")
    public void onMenuItemConfirm(final ClickEvent clickEvent) {
        boolean isValid = true;
        
        // Menu item's name input validations.
        final String name = menuItemName.getText();
        if (isEmpry(name)) {
            nameLabel.addStyleName(style.labelError());
            isValid = false;
        } else {
            nameLabel.removeStyleName(style.labelError());
        }
        
        if (selectedItemType == null) {
            menuItemTypeLabel.addStyleName(style.labelError());
            isValid = false;
        } else {
            menuItemTypeLabel.removeStyleName(style.labelError());
            if (ITEM_TYPE_COMMAND.equals(selectedItemType) && selectedPerspective == null) {
                perspectiveLabel.addStyleName(style.labelError());
                isValid = false;
            } else {
                perspectiveLabel.removeStyleName(style.labelError());
            }
        }
        
        if (isValid) {
            switch (selectedItemType) {
                case COMMAND:
                    final String pId = selectedPerspective.getIdentifier();
                    createMenuItemCommand(pId, name);
                    break;
                case GROUP:
                    createMenuItemGroup(name);
                    break;
            }
            selectedItemType = null;
            selectedPerspective = null;
            hideMenuItemModalPanel();
        }
    }
    
    private boolean isEmpry(final String text) {
        return text == null || text.trim().length() == 0;
    }

    private void createMenuItemCommand(final String activityId, final String name) {
        viewCallback.createItemCommand(name, activityId);
    }

    private void createMenuItemGroup(final String name) {
        viewCallback.createItemGroup(name);
    }

    public void enableEdition() {
        isEdit = true;
        barForm.setVisible(true);
    }

    public void disableEdition() {
        isEdit = false;
        selectedItemType = null;
        selectedPerspective = null;
        resetMenuItemForm();
        hideMenuItemModalPanel();
        barForm.setVisible(false);
    }
    
    private void showMenuItemModalPanel() {
        resetMenuItemForm();
        buildItemTypes();
        buildPerspectivesDropDown();
        menuItemModalPanel.show();
        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            @Override
            public void execute() {
                menuItemName.setFocus(true);
            }
        });
    }

    private void hideMenuItemModalPanel() {
        menuItemModalPanel.hide();
    }
    
    private void resetMenuItemForm() {
        nameLabel.removeStyleName(style.labelError());
        perspectiveLabel.removeStyleName(style.labelError());
        menuItemName.setText("");
        selectedItemType = null;
        selectedPerspective = null;
        menuItemForm.reset();
        enablePerspectivesDropDown();
    }
    
    private void enableRemove() {
        removeButtonPanel.getElement().setDraggable(Element.DRAGGABLE_TRUE);
        final AlphaAnimation alphaAnimation = new AlphaAnimation(removeButtonPanel);
        alphaAnimation.show(ALPHA_ANIMATION_DURATION);
    }

    private void disableRemove() {
        removeButtonPanel.getElement().setDraggable(Element.DRAGGABLE_FALSE);
        final AlphaAnimation alphaAnimation = new AlphaAnimation(removeButtonPanel);
        alphaAnimation.hide(ALPHA_ANIMATION_DURATION);
    }

    private void buildItemTypes() {
        final MenuComponent.MenuItemTypes[] itemTypes = MenuComponent.MenuItemTypes.values();
        
        menuItemTypesPanel.clear();
        selectedItemType = ITEM_TYPE_COMMAND;
        for (final MenuComponent.MenuItemTypes type: itemTypes) {
            final String name = MenuUtils.getItemTypeName(type).asString();
            final RadioButton button = new RadioButton(MENU_ITEM_TYPE_GROUP, name);
            button.addStyleName(style.menuItemTypeRadioButton());
            if (ITEM_TYPE_COMMAND.equals(type)) {
                button.setValue(true);
            } else {
                button.setValue(false);
            }
            button.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(final ClickEvent event) {
                    if (ITEM_TYPE_COMMAND.equals(type)) {
                        enablePerspectivesDropDown();
                    } else {
                        disablePerspectivesDropDown();
                    }
                    selectedItemType = type;
                }
            });
            menuItemTypesPanel.add(button);
        }
    }
    
    private void buildPerspectivesDropDown() {

        // Load perspectives and build the drop down.
        final Collection<PerspectiveActivity> perspectives = viewCallback.getPerspectiveActivities();

        menuItemPerspectives.clear();
        menuItemPerspectives.setText(MenusConstants.INSTANCE.perspective_placeholder() + POINTS);
        for (final PerspectiveActivity perspective : perspectives) {
            final String pName = perspective.getDefaultPerspectiveLayout().getName();
            final String name = getSafeHtml(pName);
            final NavLink link = new NavLink(name);
            link.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(final ClickEvent event) {
                    selectedPerspective = perspective;
                    menuItemPerspectives.setText(name);
                }
            });
            menuItemPerspectives.add(link);
        }
    }

    private void enablePerspectivesDropDown() {
        final AlphaAnimation alphaAnimation = new AlphaAnimation(perspectivesPanel);
        alphaAnimation.show(ALPHA_ANIMATION_DURATION);
    }

    private void disablePerspectivesDropDown() {
        final AlphaAnimation alphaAnimation = new AlphaAnimation(perspectivesPanel);
        alphaAnimation.hide(ALPHA_ANIMATION_DURATION);
    }
    
    private String getSafeHtml(final String text) {
        return new SafeHtmlBuilder().appendEscaped(text).toSafeHtml().asString();
    }
    
    private void buildMenuItems(final Menus menus) {
        if (menus != null) {
            for ( final MenuItem activeMenu : menus.getItems() ) {
                buildMenuItem(activeMenu);
            }
        }
    }

    private void buildMenuItem(final MenuItem menuItem) {
        final Widget result = makeItem( menuItem );
        if ( result != null ) {
            final Widget gwtItem = makeItem( menuItem );
            if ( menuItem.getPosition().equals( MenuPosition.LEFT ) ) {
                menuBarLeft.add( gwtItem );
            } else if ( menuItem.getPosition().equals( MenuPosition.CENTER ) ) {
                menuBarCenter.add( gwtItem );
            } else if ( menuItem.getPosition().equals( MenuPosition.RIGHT ) ) {
                menuBarRight.add( gwtItem );
            }
        }
    }

    private void configureWidgetDnd(final Widget gwtItem, final String uuid) {
        if (gwtItem != null) {
            
            // Enable GWT native Dnd.
            gwtItem.getElement().setDraggable(Element.DRAGGABLE_TRUE);

            // Drag start handler.
            gwtItem.addDomHandler(new DragStartHandler() {
                @Override
                public void onDragStart(final DragStartEvent event) {
                    event.stopPropagation();
                    onMenuItemDragStart(event, uuid, gwtItem);
                    
                }
            }, DragStartEvent.getType());

            // Drag over handler.
            gwtItem.addDomHandler(new DragOverHandler() {
                @Override
                public void onDragOver(final DragOverEvent event) {
                    event.stopPropagation();
                    onMenuItemDragOver(event, uuid, gwtItem);
                }
            }, DragOverEvent.getType());

            // Drag leave handler.
            gwtItem.addDomHandler(new DragLeaveHandler() {
                @Override
                public void onDragLeave(final DragLeaveEvent event) {
                    event.stopPropagation();
                    onMenuItemDragLeave(event, uuid, gwtItem);
                }
            }, DragLeaveEvent.getType());

            // Drag end handler.
            gwtItem.addDomHandler(new DragEndHandler() {
                @Override
                public void onDragEnd(final DragEndEvent event) {
                    event.stopPropagation();
                    onMenuItemDragEnd(event, uuid, gwtItem);
                }
            }, DragEndEvent.getType());
            
            // Drop handler.
            gwtItem.addDomHandler(new DropHandler() {
                @Override
                public void onDrop(final DropEvent event) {
                    event.stopPropagation();
                    onMenuItemDrop(event, uuid, gwtItem);
                }
            }, DropEvent.getType());
        }
    }

    private void onMenuItemDragStart(final DragStartEvent event, final String uuid, final Widget gwtItem) {
        if (isEdit) {
            event.setData("text", uuid);
            GWT.log("onMenuItemDragStart - uuid: " + uuid);
            enableRemove();
        }
    }
    
    private void onMenuItemDragOver(final DragOverEvent event, final String uuid, final Widget gwtItem) {
        if (isEdit) {
            removeMenuItemDragStyles(gwtItem);
            final boolean isMenuGroup = isMenuGroup(gwtItem); 
            if (isMenuGroup) {
                openMenuGroup(gwtItem);
            }
            final boolean isMouseOnRight = isMouseOnRightOfWidget(event, gwtItem);
            final boolean isMouseOnBottom = isMouseOnBottomOfWidget(event, gwtItem);
            if (isMouseOnBottom && !isMenuGroup) {
                gwtItem.addStyleName(style.menuItemDropOver_b());
            } else if (isMouseOnRight) {
                gwtItem.addStyleName(style.menuItemDropOver_r());
            } else {
                gwtItem.addStyleName(style.menuItemDropOver_l());
            }
        }
    }
    
    private boolean isMouseOnBottomOfWidget(final DomEvent event, final Widget w) {
        final int mouseY = event.getNativeEvent().getClientY();
        final int itemY0 = w.getAbsoluteTop();
        final int itemY1 = itemY0 + w.getElement().getOffsetHeight();
        final int itemY = itemY0 + ( (itemY1 - itemY0) / 2 );
        final boolean isMouseOnBottom = mouseY > itemY;
        return isMouseOnBottom;
    }

    private boolean isMouseOnRightOfWidget(final DomEvent event, final Widget w) {
        final int mouseX = event.getNativeEvent().getClientX();
        final int itemX0 = w.getAbsoluteLeft();
        final int itemX1 = itemX0 + w.getElement().getOffsetWidth();
        final int itemX = itemX0 + ( (itemX1 - itemX0) / 2 );
        final boolean isMouseOnRight = mouseX > itemX;
        return isMouseOnRight;
    }

    private void onMenuItemDragLeave(final DragLeaveEvent event, final String id, final Widget gwtItem) {
        if (isEdit) {
            removeMenuItemDragStyles(gwtItem);
            disableRemove();
        }
    }

    private void onMenuItemDragEnd(final DragEndEvent event, final String id, final Widget gwtItem) {
        if (isEdit) {
            removeMenuItemDragStyles(gwtItem);
            disableRemove();
        }
    }

    private void onMenuItemDrop(final DropEvent event, final String targetUUID, final Widget gwtItem) {
        if (isEdit) {
            removeMenuItemDragStyles(gwtItem);
            disableRemove();

            final String sourceUUID = event.getData("text");
            GWT.log("Drop from '" + sourceUUID + "' on  '" + targetUUID + "'");
            if (sourceUUID != null && targetUUID != null && !sourceUUID.equals(targetUUID)) {
                final boolean isMouseOnBottom = isMouseOnBottomOfWidget(event, gwtItem);
                final boolean isMouseOnRight = isMouseOnRightOfWidget(event, gwtItem);
                if (isMouseOnBottom && !isMenuGroup(gwtItem)) {
                    gwtItem.addStyleName(style.menuItemDropOver_b());
                } else if (isMouseOnRight) {
                    gwtItem.addStyleName(style.menuItemDropOver_r());
                } else {
                    gwtItem.addStyleName(style.menuItemDropOver_l());
                }
                final boolean before = !isMouseOnBottom && !isMouseOnRight;
                viewCallback.moveItem(sourceUUID, targetUUID, before);
            }
        }
    }
    
    private void openMenuGroup(final Widget gwtItem) {
        gwtItem.getElement().addClassName(DROPDOWN_OPEN_STYLE);
        startTimer(gwtItem);
    }

    private void closeMenuGroup(final Widget gwtItem) {
        gwtItem.getElement().removeClassName(DROPDOWN_OPEN_STYLE);
    }
    
    private boolean isMenuGroup(final Widget gwtItem) {
        try {
            final Dropdown dropdown = (Dropdown) gwtItem;
            return true;
        } catch (ClassCastException e) {
            return false;
        }
    }

    private void startTimer(final Widget menuGroup) {
        if (menuGroupTimer == null) {
            menuGroupTimer = new Timer() {
                @Override
                public void run() {
                    removeMenuItemDragStyles(menuGroup);
                    closeMenuGroup(menuGroup);
                    menuGroupTimer = null;
                }
            };

            menuGroupTimer.schedule(2000);
        }
    }

    private void endTimer() {
        if (menuGroupTimer != null) {
            menuGroupTimer.run();
            menuGroupTimer.cancel();
        }
    }
    
    private void removeMenuItemDragStyles(final Widget gwtItem) {
        gwtItem.removeStyleName(style.menuItemDropOver_b());
        gwtItem.removeStyleName(style.menuItemDropOver_l());
        gwtItem.removeStyleName(style.menuItemDropOver_r());
    }

    Widget makeItem( final MenuItem item ) {
        if ( notHavePermissionToMakeThis( item ) ) {
            return null;
        }

        if ( item instanceof MenuItemCommand) {

            return makeMenuItemCommand( item );

        } else if ( item instanceof MenuGroup ) {

            return makeMenuGroup( (MenuGroup) item );

        } else if ( item instanceof MenuCustom ) {

            return makeMenuCustom( (MenuCustom) item );

        }
        return makeNavLink( item );
    }

    Widget makeNavLink( final MenuItem item ) {
        final NavLink gwtItem = new NavLink( item.getCaption() ) {{
            setDisabled( !item.isEnabled() );
        }};
        item.addEnabledStateChangeListener( new EnabledStateChangeListener() {
            @Override
            public void enabledStateChanged( final boolean enabled ) {
                gwtItem.setDisabled( !enabled );
            }
        } );
        configureWidgetDnd(gwtItem, item.getSignatureId());
        return gwtItem;
    }

    Widget makeMenuCustom( MenuCustom item ) {
        final MenuCustom custom = item;
        final Widget w =((IsWidget) item.build()).asWidget();
        configureWidgetDnd(w, item.getSignatureId());
        return w;
    }

    Widget makeMenuGroup( final MenuGroup groups ) {
        final List<Widget> widgetList = new ArrayList<Widget>();
        for ( final MenuItem _item : groups.getItems() ) {
            final Widget result = makeItem( _item );
            if ( result != null ) {
                widgetList.add( result );
            }
        }

        if ( widgetList.isEmpty() ) {
            return null;
        }

        final Dropdown dropdown = new Dropdown( groups.getCaption() ) {{
            for ( final Widget widget : widgetList ) {
                add( widget );
            }
        }};

        configureWidgetDnd(dropdown, groups.getSignatureId());
        return dropdown;
    }

    Widget makeMenuItemCommand( final MenuItem item ) {
        final MenuItemCommand cmdItem = (MenuItemCommand) item;
        final Widget gwtItem;

        gwtItem = new NavLink( cmdItem.getCaption() ) {{
            setDisabled( !item.isEnabled() );
            addClickHandler( new ClickHandler() {
                @Override
                public void onClick( final ClickEvent event ) {
                    cmdItem.getCommand().execute();
                }
            } );
        }};
        item.addEnabledStateChangeListener( new EnabledStateChangeListener() {
            @Override
            public void enabledStateChanged( final boolean enabled ) {
                ( (NavLink) gwtItem ).setDisabled( !enabled );
            }
        } );
        configureWidgetDnd(gwtItem, item.getSignatureId());
        return gwtItem;
    }

    boolean notHavePermissionToMakeThis( MenuItem item ) {
        return viewCallback.notHavePermissionToMakeThis(item);
    }

    private void clearMenuBars() {
        menuBarLeft.clear();
        menuBarCenter.clear();
        menuBarRight.clear();
    }
    
    @Override
    public void clear() {
        clearMenuBars();
        selectedItemType = null;
        selectedPerspective = null;
        isEdit = false;
        endTimer();
    }

}
