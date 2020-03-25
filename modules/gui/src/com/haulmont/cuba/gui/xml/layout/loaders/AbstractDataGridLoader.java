/*
 * Copyright (c) 2008-2016 Haulmont.
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

package com.haulmont.cuba.gui.xml.layout.loaders;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.haulmont.bali.events.Subscription;
import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.chile.core.model.MetaProperty;
import com.haulmont.chile.core.model.MetaPropertyPath;
import com.haulmont.chile.core.model.MetadataObject;
import com.haulmont.cuba.core.app.dynamicattributes.DynamicAttributesUtils;
import com.haulmont.cuba.core.entity.CategoryAttribute;
import com.haulmont.cuba.core.entity.LocaleHelper;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.gui.GuiDevelopmentException;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.DataGrid.Column;
import com.haulmont.cuba.gui.components.data.DataGridItems;
import com.haulmont.cuba.gui.components.data.datagrid.ContainerDataGridItems;
import com.haulmont.cuba.gui.components.data.datagrid.EmptyDataGridItems;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.data.aggregation.AggregationStrategy;
import com.haulmont.cuba.gui.dynamicattributes.DynamicAttributesGuiTools;
import com.haulmont.cuba.gui.model.*;
import com.haulmont.cuba.gui.screen.FrameOwner;
import com.haulmont.cuba.gui.screen.UiControllerUtils;
import com.haulmont.cuba.gui.xml.layout.ComponentLoader;
import com.haulmont.cuba.gui.xml.layout.LayoutLoader;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.datatype.DatatypeElementFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.text.DecimalFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractDataGridLoader<T extends DataGrid> extends ActionsHolderLoader<T> {

    protected static final List<Class<?>> UNSUPPORTED_DECLARATIVE_RENDERERS = ImmutableList.of(
            DataGrid.ButtonRenderer.class,
            DataGrid.ClickableTextRenderer.class,
            DataGrid.ImageRenderer.class
    );

    protected static final List<Class<?>> UNSUPPORTED_PARAMETERIZED_RENDERERS = ImmutableList.of(
            DataGrid.DateRenderer.class,
            DataGrid.LocalDateRenderer.class,
            DataGrid.LocalDateTimeRenderer.class,
            DataGrid.NumberRenderer.class
    );

    protected static final Map<String, Class<?>> RENDERERS_MAP =
            ImmutableMap.<String, Class<?>>builder()
                    .put("checkBoxRenderer", DataGrid.CheckBoxRenderer.class)
                    .put("componentRenderer", DataGrid.ComponentRenderer.class)
                    .put("dateRenderer", DataGrid.DateRenderer.class)
                    .put("iconRenderer", DataGrid.IconRenderer.class)
                    .put("htmlRenderer", DataGrid.HtmlRenderer.class)
                    .put("localDateRenderer", DataGrid.LocalDateRenderer.class)
                    .put("localDateTimeRenderer", DataGrid.LocalDateTimeRenderer.class)
                    .put("numberRenderer", DataGrid.NumberRenderer.class)
                    .put("progressBarRenderer", DataGrid.ProgressBarRenderer.class)
                    .put("textRenderer", DataGrid.TextRenderer.class)
                    .build();

    private static final Logger log = LoggerFactory.getLogger(AbstractDataGridLoader.class);

    protected ComponentLoader buttonsPanelLoader;
    protected Element panelElement;

    protected String sortedColumnId;
    protected DataGrid.SortDirection sortDirection;
    protected Subscription masterDataLoaderPostLoadListener; // used for CollectionPropertyContainer

    @Override
    public void createComponent() {
        resultComponent = createComponentInternal();
        loadId(resultComponent, element);
        createButtonsPanel(resultComponent, element);
    }

    protected abstract T createComponentInternal();

    protected void createButtonsPanel(HasButtonsPanel dataGrid, Element element) {
        panelElement = element.element("buttonsPanel");
        if (panelElement != null) {
            LayoutLoader layoutLoader = getLayoutLoader();

            ButtonsPanelLoader loader = (ButtonsPanelLoader) layoutLoader.getLoader(panelElement, ButtonsPanel.NAME);
            loader.createComponent();
            ButtonsPanel panel = loader.getResultComponent();

            dataGrid.setButtonsPanel(panel);

            buttonsPanelLoader = loader;
        }
    }

    @Override
    public void loadComponent() {
        assignXmlDescriptor(resultComponent, element);
        assignFrame(resultComponent);

        loadEnable(resultComponent, element);
        loadVisible(resultComponent, element);
        loadSettingsEnabled(resultComponent, element);

        loadAlign(resultComponent, element);
        loadStyleName(resultComponent, element);

        loadHeight(resultComponent, element);
        loadWidth(resultComponent, element);

        loadIcon(resultComponent, element);
        loadCaption(resultComponent, element);
        loadDescription(resultComponent, element);
        loadContextHelp(resultComponent, element);

        loadHtmlSanitizerEnabled(resultComponent, element);

        loadEditorEnabled(resultComponent, element);
        loadEditorBuffered(resultComponent, element);
        loadEditorSaveCaption(resultComponent, element);
        loadEditorCancelCaption(resultComponent, element);
        loadEditorCrossFieldEnabled(resultComponent, element);

        loadActions(resultComponent, element);

        loadContextMenuEnabled(resultComponent, element);
        loadColumnsHidingAllowed(resultComponent, element);
        loadColumnResizeMode(resultComponent, element);
        loadSortable(resultComponent, element);
        loadResponsive(resultComponent, element);
        loadCss(resultComponent, element);
        loadReorderingAllowed(resultComponent, element);
        loadHeaderVisible(resultComponent, element);
        loadFooterVisible(resultComponent, element);
        loadTextSelectionEnabled(resultComponent, element);
        loadBodyRowHeight(resultComponent, element);
        loadHeaderRowHeight(resultComponent, element);
        loadFooterRowHeight(resultComponent, element);
        loadEmptyStateMessage(resultComponent, element);
        loadEmptyStateLinkMessage(resultComponent, element);
        loadAggregatable(resultComponent, element);
        loadAggregationPosition(resultComponent, element);

        Element columnsElement = element.element("columns");

        loadButtonsPanel(resultComponent);

        loadRowsCount(resultComponent, element); // must be before datasource setting

        MetaClass metaClass;
        CollectionContainer collectionContainer = null;
        DataLoader dataLoader = null;
        Datasource datasource = null;

        String containerId = element.attributeValue("dataContainer");
        String datasourceId = element.attributeValue("datasource");
        if (!Strings.isNullOrEmpty(containerId)) {
            FrameOwner frameOwner = getComponentContext().getFrame().getFrameOwner();
            ScreenData screenData = UiControllerUtils.getScreenData(frameOwner);
            InstanceContainer container = screenData.getContainer(containerId);
            if (container instanceof CollectionContainer) {
                collectionContainer = (CollectionContainer) container;
            } else {
                throw new GuiDevelopmentException("Not a CollectionContainer: " + containerId, context);
            }
            metaClass = collectionContainer.getEntityMetaClass();
            if (collectionContainer instanceof HasLoader) {
                dataLoader = ((HasLoader) collectionContainer).getLoader();
            }
        } else if (!Strings.isNullOrEmpty(datasourceId)) {
            datasource = getComponentContext().getDsContext().get(datasourceId);
            if (datasource == null) {
                throw new GuiDevelopmentException("Can't find datasource by name: " + datasourceId, context);
            }
            if (!(datasource instanceof CollectionDatasource)) {
                throw new GuiDevelopmentException("Not a CollectionDatasource: " + datasource, context);
            }
            metaClass = datasource.getMetaClass();
        } else {
            String metaClassStr = element.attributeValue("metaClass");
            if (Strings.isNullOrEmpty(metaClassStr)) {
                throw new GuiDevelopmentException("DataGrid doesn't have data binding",
                        context, "DataGrid ID", element.attributeValue("id"));
            }

            metaClass = getMetadata().getClassNN(metaClassStr);
        }

        List<Column> availableColumns;
        if (columnsElement != null) {
            View view = collectionContainer != null ? collectionContainer.getView()
                    : datasource != null ? datasource.getView()
                    : getViewRepository().getView(metaClass.getJavaClass(), View.LOCAL);
            availableColumns = loadColumns(resultComponent, columnsElement, metaClass, view);
        } else {
            availableColumns = new ArrayList<>();
        }

        if (collectionContainer != null) {
            if (dataLoader instanceof CollectionLoader) {
                addDynamicAttributes(resultComponent, metaClass, null, (CollectionLoader) dataLoader, availableColumns);
            }
            //noinspection unchecked
            resultComponent.setItems(createContainerDataGridSource(collectionContainer));
        } else if (datasource != null) {
            addDynamicAttributes(resultComponent, metaClass, datasource, null, availableColumns);
            resultComponent.setDatasource((CollectionDatasource) datasource);
        } else {
            addDynamicAttributes(resultComponent, metaClass, null, null, availableColumns);
            //noinspection unchecked
            resultComponent.setItems(createEmptyDataGridItems(metaClass));
        }

        loadSelectionMode(resultComponent, element);
        loadFrozenColumnCount(resultComponent, element);
        loadTabIndex(resultComponent, element);

        if (collectionContainer instanceof CollectionPropertyContainer) {
            DataLoader masterDataLoader = DataLoadersHelper.getMasterDataLoader((Nested) collectionContainer);

            masterDataLoaderPostLoadListener = masterDataLoader instanceof InstanceLoader
                    ? ((InstanceLoader) masterDataLoader).addPostLoadListener(this::onMasterDataLoaderPostLoad)
                    : masterDataLoader instanceof CollectionLoader
                    ? ((CollectionLoader) masterDataLoader).addPostLoadListener(this::onMasterDataLoaderPostLoad)
                    : null;
        }
    }

    protected void onMasterDataLoaderPostLoad(Object o) {
        setColumnSort();

        if (masterDataLoaderPostLoadListener != null) {
            masterDataLoaderPostLoadListener.remove();
        }
    }

    protected Scripting getScripting() {
        return beanLocator.get(Scripting.NAME);
    }

    protected Metadata getMetadata() {
        return beanLocator.get(Metadata.NAME);
    }

    protected ViewRepository getViewRepository() {
        return beanLocator.get(ViewRepository.NAME);
    }

    @SuppressWarnings("unchecked")
    protected DataGridItems createContainerDataGridSource(CollectionContainer container) {
        return new ContainerDataGridItems(container);
    }

    protected DataGridItems createEmptyDataGridItems(MetaClass metaClass) {
        return new EmptyDataGridItems(metaClass);
    }

    protected void loadEditorEnabled(DataGrid component, Element element) {
        String editorEnabled = element.attributeValue("editorEnabled");
        if (StringUtils.isNotEmpty(editorEnabled)) {
            component.setEditorEnabled(Boolean.parseBoolean(editorEnabled));
        }
    }

    protected void loadEditorBuffered(DataGrid component, Element element) {
        String editorBuffered = element.attributeValue("editorBuffered");
        if (StringUtils.isNotEmpty(editorBuffered)) {
            component.setEditorBuffered(Boolean.parseBoolean(editorBuffered));
        }
    }

    protected void loadEditorSaveCaption(DataGrid component, Element element) {
        String editorSaveCaption = element.attributeValue("editorSaveCaption");
        if (StringUtils.isNotEmpty(editorSaveCaption)) {
            editorSaveCaption = loadResourceString(editorSaveCaption);
            component.setEditorSaveCaption(editorSaveCaption);
        }
    }

    protected void loadEditorCancelCaption(DataGrid component, Element element) {
        String editorCancelCaption = element.attributeValue("editorCancelCaption");
        if (StringUtils.isNotEmpty(editorCancelCaption)) {
            editorCancelCaption = loadResourceString(editorCancelCaption);
            component.setEditorCancelCaption(editorCancelCaption);
        }
    }

    protected void loadEditorCrossFieldEnabled(DataGrid component, Element element) {
        String editorCrossFieldValidate = element.attributeValue("editorCrossFieldValidate");
        if (StringUtils.isNotEmpty(editorCrossFieldValidate)) {
            component.setEditorCrossFieldValidate(Boolean.parseBoolean(editorCrossFieldValidate));
        }
    }

    protected void loadColumnsHidingAllowed(DataGrid component, Element element) {
        String columnsCollapsingAllowed = element.attributeValue("columnsCollapsingAllowed");
        if (StringUtils.isNotEmpty(columnsCollapsingAllowed)) {
            component.setColumnsCollapsingAllowed(Boolean.parseBoolean(columnsCollapsingAllowed));
        }
    }

    protected void loadColumnResizeMode(DataGrid component, Element element) {
        String columnResizeMode = element.attributeValue("columnResizeMode");
        if (StringUtils.isNotEmpty(columnResizeMode)) {
            component.setColumnResizeMode(DataGrid.ColumnResizeMode.valueOf(columnResizeMode));
        }
    }

    protected void loadSortable(DataGrid component, Element element) {
        String sortable = element.attributeValue("sortable");
        if (StringUtils.isNotEmpty(sortable)) {
            component.setSortable(Boolean.parseBoolean(sortable));
        }
    }

    protected void loadReorderingAllowed(DataGrid component, Element element) {
        String reorderingAllowed = element.attributeValue("reorderingAllowed");
        if (StringUtils.isNotEmpty(reorderingAllowed)) {
            component.setColumnReorderingAllowed(Boolean.parseBoolean(reorderingAllowed));
        }
    }

    protected void loadTextSelectionEnabled(DataGrid dataGrid, Element element) {
        String textSelectionEnabled = element.attributeValue("textSelectionEnabled");
        if (StringUtils.isNotEmpty(textSelectionEnabled)) {
            dataGrid.setTextSelectionEnabled(Boolean.parseBoolean(textSelectionEnabled));
        }
    }

    protected void loadBodyRowHeight(DataGrid dataGrid, Element element) {
        Integer bodyRowHeight = loadSizeInPx(element, "bodyRowHeight");
        if (bodyRowHeight != null) {
            dataGrid.setBodyRowHeight(bodyRowHeight);
        }
    }

    protected void loadHeaderRowHeight(DataGrid dataGrid, Element element) {
        Integer headerRowHeight = loadSizeInPx(element, "headerRowHeight");
        if (headerRowHeight != null) {
            dataGrid.setHeaderRowHeight(headerRowHeight);
        }
    }

    protected void loadFooterRowHeight(DataGrid dataGrid, Element element) {
        Integer footerRowHeight = loadSizeInPx(element, "footerRowHeight");
        if (footerRowHeight != null) {
            dataGrid.setFooterRowHeight(footerRowHeight);
        }
    }

    protected void loadHeaderVisible(DataGrid component, Element element) {
        String columnHeaderVisible = element.attributeValue("headerVisible");
        if (StringUtils.isNotEmpty(columnHeaderVisible)) {
            component.setHeaderVisible(Boolean.parseBoolean(columnHeaderVisible));
        }
    }

    protected void loadFooterVisible(DataGrid component, Element element) {
        String columnFooterVisible = element.attributeValue("footerVisible");
        if (StringUtils.isNotEmpty(columnFooterVisible)) {
            component.setFooterVisible(Boolean.parseBoolean(columnFooterVisible));
        }
    }

    protected void loadContextMenuEnabled(DataGrid dataGrid, Element element) {
        String contextMenuEnabled = element.attributeValue("contextMenuEnabled");
        if (StringUtils.isNotEmpty(contextMenuEnabled)) {
            dataGrid.setContextMenuEnabled(Boolean.parseBoolean(contextMenuEnabled));
        }
    }

    protected void loadButtonsPanel(DataGrid component) {
        if (buttonsPanelLoader != null) {
            buttonsPanelLoader.loadComponent();
            ButtonsPanel panel = (ButtonsPanel) buttonsPanelLoader.getResultComponent();

            String alwaysVisible = panelElement.attributeValue("alwaysVisible");
            if (alwaysVisible != null) {
                panel.setAlwaysVisible(Boolean.parseBoolean(alwaysVisible));
            }

            if (panel.getCaption() != null) {
                log.debug("The caption '{}' of ButtonsPanel inside of DataGrid will be ignored",
                        panel.getCaption());
            }
        }
    }

    protected void loadRowsCount(DataGrid component, Element element) {
        Element rowsCountElement = element.element("rowsCount");
        if (rowsCountElement != null) {
            RowsCount rowsCount = factory.create(RowsCount.class);

            String autoLoad = rowsCountElement.attributeValue("autoLoad");
            if (StringUtils.isNotEmpty(autoLoad)) {
                rowsCount.setAutoLoad(Boolean.parseBoolean(autoLoad));
            }

            rowsCount.setRowsCountTarget(component);
            component.setRowsCount(rowsCount);
        }
    }

    protected List<Column> loadColumnsByInclude(DataGrid component, Element columnsElement, MetaClass metaClass, View view) {
        Collection<String> appliedProperties = getAppliedProperties(columnsElement, view, metaClass);

        List<Column> columns = new ArrayList<>(appliedProperties.size());
        List<Element> columnElements = columnsElement.elements("column");
        Set<Element> overriddenColumns = new HashSet<>();

        DocumentFactory documentFactory = DatatypeElementFactory.getInstance();

        for (String property : appliedProperties) {
            Element column = getOverriddenColumn(columnElements, property);
            if (column == null) {
                column = documentFactory.createElement("column");
                column.add(documentFactory.createAttribute(column, "property", property));
            } else {
                overriddenColumns.add(column);
            }

            columns.add(loadColumn(component, column, metaClass));
        }

        // load remains columns
        List<Element> remainedColumns = columnsElement.elements("column");
        for (Element column : remainedColumns) {
            if (overriddenColumns.contains(column)) {
                continue;
            }

            // check property and add
            String propertyId = column.attributeValue("property");
            if (StringUtils.isNotEmpty(propertyId)) {
                MetaPropertyPath dynamicAttributePath = DynamicAttributesUtils.getMetaPropertyPath(metaClass, propertyId);

                MetaPropertyPath mpp = metaClass.getPropertyPath(propertyId);
                boolean isViewContainsProperty = mpp != null && getMetadataTools().viewContainsProperty(view, mpp);

                if (isViewContainsProperty || dynamicAttributePath != null) {
                    columns.add(loadColumn(component, column, metaClass));
                }
            }
        }

        return columns;
    }

    protected List<Column> loadColumns(DataGrid component, Element columnsElement, MetaClass metaClass, View view) {
        String includeAll = columnsElement.attributeValue("includeAll");
        if (StringUtils.isNotBlank(includeAll)) {
            if (Boolean.parseBoolean(includeAll)) {
                return loadColumnsByInclude(component, columnsElement, metaClass, view);
            }
        }

        List<Element> columnElements = columnsElement.elements("column");

        List<Column> columns = new ArrayList<>(columnElements.size());
        for (Element columnElement : columnElements) {
            columns.add(loadColumn(component, columnElement, metaClass));
        }
        return columns;
    }

    protected Column loadColumn(DataGrid component, Element element, MetaClass metaClass) {
        String id = element.attributeValue("id");
        String property = element.attributeValue("property");

        if (id == null) {
            if (property != null) {
                id = property;
            } else {
                throw new GuiDevelopmentException("A column must have whether id or property specified",
                        context, "DataGrid ID", component.getId());
            }
        }

        Column column;
        if (property != null) {
            MetaPropertyPath metaPropertyPath = getMetadataTools().resolveMetaPropertyPath(metaClass, property);
            column = component.addColumn(id, metaPropertyPath);
        } else {
            column = component.addColumn(id, null);
        }

        String expandRatio = element.attributeValue("expandRatio");
        if (StringUtils.isNotEmpty(expandRatio)) {
            column.setExpandRatio(Integer.parseInt(expandRatio));
        }

        String collapsed = element.attributeValue("collapsed");
        if (StringUtils.isNotEmpty(collapsed)) {
            column.setCollapsed(Boolean.parseBoolean(collapsed));
        }

        String collapsible = element.attributeValue("collapsible");
        if (StringUtils.isNotEmpty(collapsible)) {
            column.setCollapsible(Boolean.parseBoolean(collapsible));
        }

        String collapsingToggleCaption = element.attributeValue("collapsingToggleCaption");
        if (StringUtils.isNotEmpty(collapsingToggleCaption)) {
            collapsingToggleCaption = loadResourceString(collapsingToggleCaption);
            column.setCollapsingToggleCaption(collapsingToggleCaption);
        }

        String sortable = element.attributeValue("sortable");
        if (StringUtils.isNotEmpty(sortable)) {
            column.setSortable(Boolean.parseBoolean(sortable));
        }

        String resizable = element.attributeValue("resizable");
        if (StringUtils.isNotEmpty(resizable)) {
            column.setResizable(Boolean.parseBoolean(resizable));
        }

        String editable = element.attributeValue("editable");
        if (StringUtils.isNotEmpty(editable)) {
            column.setEditable(Boolean.parseBoolean(editable));
        }

        String sort = element.attributeValue("sort");
        if (StringUtils.isNotBlank(sort)) {
            loadColumnSort(component, column, sort);
        }

        String caption = loadCaption(element);

        if (caption == null) {
            String columnCaption;
            if (column.getPropertyPath() != null) {
                MetaProperty metaProperty = column.getPropertyPath().getMetaProperty();
                String propertyName = metaProperty.getName();

                if (DynamicAttributesUtils.isDynamicAttribute(metaProperty)) {
                    CategoryAttribute categoryAttribute = DynamicAttributesUtils.getCategoryAttribute(metaProperty);
                    columnCaption = LocaleHelper.isLocalizedValueDefined(categoryAttribute.getLocaleNames()) ?
                            categoryAttribute.getLocaleName() :
                            StringUtils.capitalize(categoryAttribute.getName());
                } else {
                    MetaClass propertyMetaClass = getMetadataTools().getPropertyEnclosingMetaClass(column.getPropertyPath());

                    columnCaption = getMessageTools().getPropertyCaption(propertyMetaClass, propertyName);
                }
            } else {
                Class<?> declaringClass = metaClass.getJavaClass();
                String className = declaringClass.getName();
                int i = className.lastIndexOf('.');
                if (i > -1) {
                    className = className.substring(i + 1);
                }
                columnCaption = getMessages().getMessage(declaringClass, className + "." + id);
            }
            column.setCaption(columnCaption);
        } else {
            column.setCaption(caption);
        }

        ((Component.HasXmlDescriptor) column).setXmlDescriptor(element);

        Integer width = loadSizeInPx(element, "width");
        if (width != null) {
            column.setWidth(width);
        }

        Integer minimumWidth = loadSizeInPx(element, "minimumWidth");
        if (minimumWidth != null) {
            column.setMinimumWidth(minimumWidth);
        }

        Integer maximumWidth = loadSizeInPx(element, "maximumWidth");
        if (maximumWidth != null) {
            column.setMaximumWidth(maximumWidth);
        }

        column.setGeneratedType(loadGeneratedType(element));

        column.setFormatter(loadFormatter(element));
        column.setRenderer(loadRenderer(element));

        loadAggregation(column, element);

        return column;
    }

    @Nullable
    protected DataGrid.Renderer loadRenderer(Element columnElement) {
        Element rendererElement;

        for (Map.Entry<String, Class<?>> entry : RENDERERS_MAP.entrySet()) {
            rendererElement = columnElement.element(entry.getKey());
            if (rendererElement != null) {
                return loadRendererByClass(rendererElement, entry.getValue());
            }
        }

        rendererElement = columnElement.element("renderer");
        if (rendererElement != null) {
            return loadLegacyRenderer(rendererElement);
        }

        return null;
    }

    @SuppressWarnings("rawtypes")
    protected DataGrid.Renderer loadRendererByClass(Element rendererElement, Class rendererClass) {
        DataGrid.Renderer renderer = resultComponent.createRenderer(rendererClass);

        if (renderer instanceof DataGrid.HasNullRepresentation) {
            loadNullRepresentation(rendererElement, (DataGrid.HasNullRepresentation) renderer);
        }

        if (renderer instanceof DataGrid.HasDateTimeFormatter) {
            loadFormatPattern(rendererElement, (DataGrid.HasDateTimeFormatter) renderer);
        }

        if (renderer instanceof DataGrid.HasFormatString) {
            loadFormatString(rendererElement, (DataGrid.HasFormatString) renderer);
        }

        return renderer;
    }

    @Nullable
    protected DataGrid.Renderer loadLegacyRenderer(Element renderer) {
        String rendererType = renderer.attributeValue("type");
        if (StringUtils.isEmpty(rendererType)) {
            return null;
        }

        Class<?> rendererClass = getScripting().loadClassNN(rendererType);

        if (UNSUPPORTED_PARAMETERIZED_RENDERERS.contains(rendererClass)) {
            throw new GuiDevelopmentException(String.format(
                    "DataGrid doesn't support renderer of type '%s' without required parameters. " +
                            "Use special XML elements for parameterized renderers.",
                    rendererType), context);
        }

        if (UNSUPPORTED_DECLARATIVE_RENDERERS.contains(rendererClass)) {
            throw new GuiDevelopmentException(String.format(
                    "DataGrid doesn't support declarative configuration of renderer of type '%s'. " +
                            "Define it in screen controller.",
                    rendererType), context);
        }

        return resultComponent.createRenderer(rendererClass);
    }

    protected void loadNullRepresentation(Element rendererElement, DataGrid.HasNullRepresentation renderer) {
        String nullRepresentation = rendererElement.attributeValue("nullRepresentation");
        if (StringUtils.isNotEmpty(nullRepresentation)) {
            renderer.setNullRepresentation(nullRepresentation);
        }
    }

    protected void loadFormatPattern(Element rendererElement, DataGrid.HasDateTimeFormatter renderer) {
        String formatPattern = rendererElement.attributeValue("format");
        if (StringUtils.isNotEmpty(formatPattern)) {
            renderer.setFormatPattern(formatPattern);
        }
    }

    protected void loadFormatString(Element rendererElement, DataGrid.HasFormatString renderer) {
        String formatString = rendererElement.attributeValue("format");
        if (StringUtils.isNotEmpty(formatString)) {
            renderer.setFormatString(formatString);
        }
    }

    @Nullable
    protected Class loadGeneratedType(Element columnElement) {
        String colGenType = columnElement.attributeValue("generatedType");
        if (StringUtils.isNotEmpty(colGenType)) {
            return getScripting().loadClassNN(colGenType);
        }
        return null;
    }

    @Nullable
    protected String loadCaption(Element element) {
        if (element.attribute("caption") != null) {
            String caption = element.attributeValue("caption");

            return loadResourceString(caption);
        }
        return null;
    }

    @Nullable
    protected Integer loadSizeInPx(Element element, String propertyName) {
        String value = loadThemeString(element.attributeValue(propertyName));
        if (!StringUtils.isBlank(value)) {
            if (StringUtils.endsWith(value, "px")) {
                value = StringUtils.substring(value, 0, value.length() - 2);
            }
            try {
                // Only integer allowed in XML
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new GuiDevelopmentException("Property '" + propertyName + "' must contain only numeric value",
                        context, propertyName, element.attributeValue(propertyName));
            }
        }
        return null;
    }

    protected MetadataTools getMetadataTools() {
        return beanLocator.get(MetadataTools.NAME);
    }

    protected DynamicAttributesGuiTools getDynamicAttributesGuiTools() {
        return beanLocator.get(DynamicAttributesGuiTools.NAME);
    }

    protected void addDynamicAttributes(DataGrid component, MetaClass metaClass,
                                        @Nullable Datasource ds, @Nullable CollectionLoader collectionLoader,
                                        List<Column> availableColumns) {
        if (getMetadataTools().isPersistent(metaClass)) {
            String windowId = getWindowId(context);
            // May be no windowId, if a loader is used from a CompositeComponent
            if (windowId == null) {
                return;
            }
            Set<CategoryAttribute> attributesToShow =
                    getDynamicAttributesGuiTools().getAttributesToShowOnTheScreen(metaClass,
                            windowId, component.getId());
            if (CollectionUtils.isNotEmpty(attributesToShow)) {
                if (collectionLoader != null) {
                    collectionLoader.setLoadDynamicAttributes(true);
                } else if (ds != null) {
                    ds.setLoadDynamicAttributes(true);
                }
                for (CategoryAttribute attribute : attributesToShow) {
                    final MetaPropertyPath metaPropertyPath =
                            DynamicAttributesUtils.getMetaPropertyPath(metaClass, attribute);

                    Object columnWithSameId = IterableUtils.find(availableColumns, column -> {
                        MetaPropertyPath propertyPath = column.getPropertyPath();
                        return propertyPath != null && propertyPath.equals(metaPropertyPath);
                    });

                    if (columnWithSameId != null) {
                        continue;
                    }

                    addDynamicAttributeColumn(component, attribute, metaPropertyPath);
                }
            }

            if (ds != null) {
                getDynamicAttributesGuiTools().listenDynamicAttributesChanges(ds);
            }
        }
    }

    protected void addDynamicAttributeColumn(DataGrid component, CategoryAttribute attribute, MetaPropertyPath metaPropertyPath) {

        final Column column =
                component.addColumn(metaPropertyPath.getMetaProperty().getName(), metaPropertyPath);

        column.setCaption(getDynamicAttributesGuiTools().getColumnCapture(attribute));

        //noinspection unchecked
        column.setDescriptionProvider(value -> attribute.getLocaleDescription());

        DecimalFormat formatter = getDynamicAttributesGuiTools().getDecimalFormat(attribute);
        if (formatter != null) {
            column.setFormatter(obj -> {
                if (obj == null) {
                    return null;
                }
                if (obj instanceof Number) {
                    return formatter.format(obj);
                }
                return obj.toString();
            });
        }

        if (attribute.getConfiguration().getColumnWidth() != null) {
            column.setWidth(attribute.getConfiguration().getColumnWidth());
        }
    }

    protected void loadSelectionMode(DataGrid component, Element element) {
        String selectionMode = element.attributeValue("selectionMode");
        if (StringUtils.isNotEmpty(selectionMode)) {
            component.setSelectionMode(DataGrid.SelectionMode.valueOf(selectionMode));
        }
    }

    protected void loadFrozenColumnCount(DataGrid component, Element element) {
        String frozenColumnCount = element.attributeValue("frozenColumnCount");
        if (StringUtils.isNotEmpty(frozenColumnCount)) {
            component.setFrozenColumnCount(Integer.parseInt(frozenColumnCount));
        }
    }

    protected Collection<String> getAppliedProperties(Element columnsElement, View view, MetaClass metaClass) {
        String exclude = columnsElement.attributeValue("exclude");
        List<String> excludes = StringUtils.isEmpty(exclude) ? Collections.emptyList() :
                Splitter.on(",").omitEmptyStrings().trimResults().splitToList(exclude);

        MetadataTools metadataTools = getMetadataTools();

        Stream<String> properties;
        if (metadataTools.isPersistent(metaClass) && view != null) {
            properties = view.getProperties().stream().map(ViewProperty::getName);
        } else {
            properties = metaClass.getProperties().stream().map(MetadataObject::getName);
        }

        List<String> appliedProperties = properties.filter(s -> !excludes.contains(s)).collect(Collectors.toList());

        return appliedProperties;
    }

    @Nullable
    protected Element getOverriddenColumn(List<Element> columns, String property) {
        for (Element element : columns) {
            String propertyAttr = element.attributeValue("property");
            if (StringUtils.isNotEmpty(propertyAttr) && propertyAttr.equals(property)) {
                return element;
            }
        }
        return null;
    }

    protected void loadColumnSort(DataGrid component, Column column, String sort) {
        if (sortedColumnId != null) {
            throw new GuiDevelopmentException(String.format("Column '%s' cannot be sorted because DataGrid have already" +
                    " sorted '%s' column", column.getId(), sortedColumnId), getContext());
        }

        if (column.getPropertyPath() == null) {
            throw new GuiDevelopmentException(
                    String.format("Can't sort column '%s' because it is not bounded with entity's property", column.getId()),
                    getContext());
        }

        if (!column.isSortable()) {
            throw new GuiDevelopmentException(
                    String.format("Can't sort column '%s' because it is disabled for sorting by 'sortable' attribute", column.getId()),
                    getContext());
        }

        sortDirection = DataGrid.SortDirection.valueOf(sort);
        sortedColumnId = column.getId();
        getComponentContext().addPostInitTask((context, window) -> setColumnSort());
    }

    protected void setColumnSort() {
        if (sortedColumnId != null && sortDirection != null) {
            resultComponent.sort(sortedColumnId, sortDirection);
        }
    }

    protected void loadEmptyStateMessage(DataGrid dataGrid, Element element) {
        String emptyStateMessage = element.attributeValue("emptyStateMessage");
        if (StringUtils.isNotBlank(emptyStateMessage)) {
            dataGrid.setEmptyStateMessage(emptyStateMessage);
        }
    }

    protected void loadEmptyStateLinkMessage(DataGrid dataGrid, Element element) {
        String emptyStateLinkMessage = element.attributeValue("emptyStateLinkMessage");
        if (StringUtils.isNotBlank(emptyStateLinkMessage)) {
            dataGrid.setEmptyStateLinkMessage(emptyStateLinkMessage);
        }
    }

    protected void loadAggregatable(DataGrid component, Element element) {
        String aggregatable = element.attributeValue("aggregatable");
        if (StringUtils.isNotEmpty(aggregatable)) {
            component.setAggregatable(Boolean.parseBoolean(aggregatable));
        }
    }

    protected void loadAggregationPosition(DataGrid component, Element element) {
        String aggregationPosition = element.attributeValue("aggregationPosition");
        if (!StringUtils.isEmpty(aggregationPosition)) {
            component.setAggregationPosition(DataGrid.AggregationPosition.valueOf(aggregationPosition));
        }
    }

    @SuppressWarnings("unchecked")
    protected void loadAggregation(DataGrid.Column column, Element columnElement) {
        Element aggregationElement = columnElement.element("aggregation");
        if (aggregationElement != null) {
            AggregationInfo aggregation = new AggregationInfo();
            aggregation.setPropertyPath(column.getPropertyPath());

            loadAggregationType(aggregation, aggregationElement);

            loadValueDescription(column, aggregationElement);

            Function formatter = loadFormatter(aggregationElement);
            aggregation.setFormatter(formatter == null ? column.getDescriptionProvider() : formatter);
            column.setAggregation(aggregation);

            loadStrategyClass(aggregation, aggregationElement);

            if (aggregation.getType() == null && aggregation.getStrategy() == null) {
                throw new GuiDevelopmentException("Incorrect aggregation - type or strategyClass is required", context);
            }
        }
    }

    protected void loadAggregationType(AggregationInfo aggregation, Element aggregationElement) {
        String aggregationType = aggregationElement.attributeValue("type");
        if (StringUtils.isNotEmpty(aggregationType)) {
            aggregation.setType(AggregationInfo.Type.valueOf(aggregationType));
        }
    }

    protected void loadValueDescription(DataGrid.Column column, Element aggregationElement) {
        String valueDescription = aggregationElement.attributeValue("valueDescription");
        if (StringUtils.isNotEmpty(valueDescription)) {
            column.setValueDescription(loadResourceString(valueDescription));
        }
    }

    protected void loadStrategyClass(AggregationInfo aggregation, Element aggregationElement) {
        String strategyClass = aggregationElement.attributeValue("strategyClass");
        if (StringUtils.isNotEmpty(strategyClass)) {
            Class<?> aggregationClass = getScripting().loadClass(strategyClass);
            if (aggregationClass == null) {
                throw new GuiDevelopmentException(String.format("Class %s is not found", strategyClass), context);
            }

            try {
                Constructor<?> constructor = aggregationClass.getDeclaredConstructor();
                AggregationStrategy customStrategy = (AggregationStrategy) constructor.newInstance();
                aggregation.setStrategy(customStrategy);
            } catch (Exception e) {
                throw new RuntimeException("Unable to instantiate strategy for aggregation", e);
            }
        }
    }
}