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
package org.dashbuilder.dataprovider.backend.sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;
import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.dashbuilder.dataprovider.DataSetProvider;
import org.dashbuilder.dataprovider.DataSetProviderType;
import org.dashbuilder.dataprovider.backend.StaticDataSetProvider;
import org.dashbuilder.dataprovider.backend.sql.dialect.Dialect;
import org.dashbuilder.dataprovider.backend.sql.model.Column;
import org.dashbuilder.dataprovider.backend.sql.model.Condition;
import org.dashbuilder.dataprovider.backend.sql.model.Select;
import org.dashbuilder.dataprovider.backend.sql.model.SortColumn;
import org.dashbuilder.dataprovider.backend.sql.model.Table;
import org.dashbuilder.dataset.ColumnType;
import org.dashbuilder.dataset.DataColumn;
import org.dashbuilder.dataset.DataSet;
import org.dashbuilder.dataset.DataSetFactory;
import org.dashbuilder.dataset.DataSetLookup;
import org.dashbuilder.dataset.DataSetMetadata;
import org.dashbuilder.dataset.DataSetOp;
import org.dashbuilder.dataset.DataSetOpEngine;
import org.dashbuilder.dataset.backend.BackendIntervalBuilderDynamicDate;
import org.dashbuilder.dataset.backend.date.DateUtils;
import org.dashbuilder.dataset.date.TimeFrame;
import org.dashbuilder.dataset.def.DataColumnDef;
import org.dashbuilder.dataset.def.DataSetDef;
import org.dashbuilder.dataset.def.DataSetDefRegistry;
import org.dashbuilder.dataset.def.SQLDataSetDef;
import org.dashbuilder.dataset.engine.group.IntervalBuilder;
import org.dashbuilder.dataset.engine.group.IntervalBuilderLocator;
import org.dashbuilder.dataset.engine.group.IntervalList;
import org.dashbuilder.dataset.events.DataSetDefModifiedEvent;
import org.dashbuilder.dataset.events.DataSetDefRemovedEvent;
import org.dashbuilder.dataset.events.DataSetStaleEvent;
import org.dashbuilder.dataset.filter.ColumnFilter;
import org.dashbuilder.dataset.filter.CoreFunctionFilter;
import org.dashbuilder.dataset.filter.CoreFunctionType;
import org.dashbuilder.dataset.filter.DataSetFilter;
import org.dashbuilder.dataset.filter.FilterFactory;
import org.dashbuilder.dataset.filter.LogicalExprFilter;
import org.dashbuilder.dataset.filter.LogicalExprType;
import org.dashbuilder.dataset.group.AggregateFunctionType;
import org.dashbuilder.dataset.group.ColumnGroup;
import org.dashbuilder.dataset.group.DataSetGroup;
import org.dashbuilder.dataset.group.DateIntervalType;
import org.dashbuilder.dataset.group.GroupFunction;
import org.dashbuilder.dataset.group.GroupStrategy;
import org.dashbuilder.dataset.group.Interval;
import org.dashbuilder.dataset.impl.DataColumnImpl;
import org.dashbuilder.dataset.impl.DataSetMetadataImpl;
import org.dashbuilder.dataset.impl.MemSizeEstimator;
import org.dashbuilder.dataset.sort.ColumnSort;
import org.dashbuilder.dataset.sort.DataSetSort;
import org.dashbuilder.dataset.sort.SortOrder;
import org.slf4j.Logger;

import static org.dashbuilder.dataprovider.backend.sql.SQLFactory.*;

/**
 *  DataSetProvider implementation for JDBC-compliant data sources.
 *
 *  <p>The SQL provider resolves every data set lookup request by transforming such request into the proper SQL query.
 *  In some cases, an extra processing of the resulting data is required since some lookup requests do not map directly
 *  into the SQL world. In such cases, specially the grouping of date based data, the core data set operation engine is
 *  used.</p>
 *
 *  <p>
 *      Pending stuff:
 *      - Filter on foreign data sets
 *      - Group (fixed) by date of week
 *  </p>
 */
@ApplicationScoped
@Named("sql")
public class SQLDataSetProvider implements DataSetProvider {

    @Inject
    protected Logger log;

    @Inject
    protected StaticDataSetProvider staticDataSetProvider;

    @Inject
    protected SQLDataSourceLocator dataSourceLocator;

    @Inject
    protected IntervalBuilderLocator intervalBuilderLocator;

    @Inject
    protected BackendIntervalBuilderDynamicDate intervalBuilderDynamicDate;

    @Inject
    protected DataSetDefRegistry dataSetDefRegistry;

    @Inject
    protected DataSetOpEngine opEngine;

    public DataSetProviderType getType() {
        return DataSetProviderType.SQL;
    }

    public DataSet lookupDataSet(DataSetDef def, DataSetLookup lookup) throws Exception {
        SQLDataSetDef sqlDef = (SQLDataSetDef) def;
        if (StringUtils.isBlank(sqlDef.getDataSource())) {
            throw new IllegalArgumentException("Missing data source in SQL data set definition: " + sqlDef);
        }
        if (StringUtils.isBlank(sqlDef.getDbSQL()) && StringUtils.isBlank(sqlDef.getDbTable())) {
            throw new IllegalArgumentException("Missing DB table or SQL in the data set definition: " + sqlDef);
        }

        // Look first into the static data set provider cache.
        if (sqlDef.isCacheEnabled()) {
            DataSet dataSet = staticDataSetProvider.lookupDataSet(def.getUUID(), null);
            if (dataSet != null) {

                // Lookup from cache.
                return staticDataSetProvider.lookupDataSet(def.getUUID(), lookup);
            } else  {

                // Fetch always from database if existing rows are greater than the cache max. rows
                int rows = getRowCount(sqlDef);
                if (rows > sqlDef.getCacheMaxRows()) {
                    return _lookupDataSet(sqlDef, lookup);
                }
                // Fetch from database and register into the static cache. Further requests will lookup from cache.
                dataSet = _lookupDataSet(sqlDef, null);
                dataSet.setUUID(def.getUUID());
                dataSet.setDefinition(def);
                staticDataSetProvider.registerDataSet(dataSet);
                return staticDataSetProvider.lookupDataSet(def.getUUID(), lookup);
            }
        }

        // If cache is disabled then always fetch from database.
        return _lookupDataSet(sqlDef, lookup);
    }

    public boolean isDataSetOutdated(DataSetDef def) {

        // Non fetched data sets can't get outdated.
        MetadataHolder last = _metadataMap.remove(def.getUUID());
        if (last == null) return false;

        // Check if the metadata has changed since the last time it was fetched.
        try {
            DataSetMetadata current = getDataSetMetadata(def);
            return !current.equals(last.metadata);
        }
        catch (Exception e) {
            log.error("Error fetching metadata: " + def, e);
            return false;
        }
    }

    public DataSetMetadata getDataSetMetadata(DataSetDef def) throws Exception {
        SQLDataSetDef sqlDef = (SQLDataSetDef) def;
        DataSource ds = dataSourceLocator.lookup(sqlDef);
        Connection conn = ds.getConnection();
        try {
            return _getDataSetMetadata(sqlDef, conn, true);
        } finally {
            conn.close();
        }
    }

    public int getRowCount(SQLDataSetDef def) throws Exception {
        DataSource ds = dataSourceLocator.lookup(def);
        Connection conn = ds.getConnection();
        try {
            return _getRowCount(def, conn);
        } finally {
            conn.close();
        }
    }

    // Listen to changes on the data set definition registry

    protected void onDataSetStaleEvent(@Observes DataSetStaleEvent event) {
        DataSetDef def = event.getDataSetDef();
        if (DataSetProviderType.SQL.equals(def.getProvider())) {
            String uuid = def.getUUID();
            staticDataSetProvider.removeDataSet(uuid);
        }
    }

    protected void onDataSetDefRemovedEvent(@Observes DataSetDefRemovedEvent event) {
        DataSetDef def = event.getDataSetDef();
        if (DataSetProviderType.SQL.equals(def.getProvider())) {
            String uuid = def.getUUID();
            _metadataMap.remove(uuid);
            staticDataSetProvider.removeDataSet(uuid);
        }
    }


    protected void onDataSetDefModifiedEvent(@Observes DataSetDefModifiedEvent event) {
        DataSetDef def = event.getOldDataSetDef();
        if (DataSetProviderType.SQL.equals(def.getProvider())) {
            String uuid = def.getUUID();
            _metadataMap.remove(uuid);
            staticDataSetProvider.removeDataSet(uuid);
        }
    }

    // Internal implementation logic

    protected class MetadataHolder {

        DataSetMetadata metadata;
        Collection<Column> columns;

        public Column getColumn(String name) {
            for (Column column : columns) {
                if (column.getName().equals(name)) {
                    return column;
                }
            }
            return null;
        }
    }

    protected transient Map<String,MetadataHolder> _metadataMap = new HashMap<String,MetadataHolder>();

    protected DataSetMetadata _getDataSetMetadata(SQLDataSetDef def, Connection conn, boolean isTestMode) throws Exception {
        if (!isTestMode) {
            MetadataHolder result = _metadataMap.get(def.getUUID());
            if (result != null) {
                return result.metadata;
            }
        }

        int estimatedSize = 0;
        int rowCount = _getRowCount(def, conn);
        List<String> columnIds = new ArrayList<String>();
        List<ColumnType> columnTypes = new ArrayList<ColumnType>();

        if (def.getColumns() != null) {
            for (DataColumnDef column : def.getColumns()) {
                String columnId = JDBCUtils.changeCase(conn, column.getId());
                columnIds.add(columnId);
                columnTypes.add(column.getColumnType());
            }
        }

        Collection<Column> _columns = _getColumns(def, conn);
        for (Column _column : _columns) {

            String columnId = _column.getName();
            int columnIdx = columnIds.indexOf(columnId);
            boolean columnExists  = columnIdx != -1;

            // Add or skip non-existing columns depending on the data set definition.
            if (!columnExists) {

                // Add any table column
                if (def.isAllColumnsEnabled()) {
                    columnIds.add(columnId);
                    columnIdx = columnIds.size()-1;
                    columnTypes.add(_column.getType());
                }
                // Skip non existing columns
                else {
                    continue;
                }
            }

            // Calculate the estimated size
            ColumnType cType = columnTypes.get(columnIdx);
            if (ColumnType.DATE.equals(cType)) {
                estimatedSize += MemSizeEstimator.sizeOf(Date.class) * rowCount;
            }
            else if (ColumnType.NUMBER.equals(cType)) {
                estimatedSize += MemSizeEstimator.sizeOf(Double.class) * rowCount;
            }
            else {
                int length = _column.getLength();
                estimatedSize += length/2 * rowCount;
            }
        }
        if (columnIds.isEmpty()) {
            throw new IllegalArgumentException("No data set columns defined for the table '" + def.getDbTable() +
                    " in database '" + def.getDataSource()+ "'");
        }
        MetadataHolder result = new MetadataHolder();
        result.columns = _columns;
        result.metadata = new DataSetMetadataImpl(def, def.getUUID(), rowCount, columnIds.size(), columnIds, columnTypes, estimatedSize);
        if (!isTestMode) {
            final boolean isDefRegistered = def.getUUID() != null && dataSetDefRegistry.getDataSetDef(def.getUUID()) != null;
            if (isDefRegistered) {
                _metadataMap.put(def.getUUID(), result);
            }
        }
        return result.metadata;
    }

    protected Collection<Column> _getColumns(SQLDataSetDef def, Connection conn) throws Exception {
        Dialect dialect = JDBCUtils.dialect(conn);
        if (!StringUtils.isBlank(def.getDbSQL())) {
            Select query = select(conn).from(def.getDbSQL()).limit(1);
            return JDBCUtils.getColumns(logSQL(query).fetch(), dialect.getExcludedColumns());
        }
        else {
            Select query = select(conn).from(_createTable(def)).limit(1);
            return JDBCUtils.getColumns(logSQL(query).fetch(), dialect.getExcludedColumns());
        }
    }

    protected int _getRowCount(SQLDataSetDef def, Connection conn) throws Exception {

        // Count rows, either on an SQL or a DB table
        Select _query = select(conn);
        _appendFrom(def, _query);

        // Filters set must be taken into account
        DataSetFilter filterOp = def.getDataSetFilter();
        if (filterOp != null) {
            List<ColumnFilter> filterList = filterOp.getColumnFilterList();
            for (ColumnFilter filter : filterList) {
                _appendFilterBy(def, filter, _query);
            }
        }
        return _query.fetchCount();
    }

    protected DataSet _lookupDataSet(SQLDataSetDef def, DataSetLookup lookup) throws Exception {
        LookupProcessor processor = new LookupProcessor(def, lookup);
        return processor.run();
    }

    protected Table _createTable(SQLDataSetDef def) {
        if (StringUtils.isBlank(def.getDbSchema())) return table(def.getDbTable());
        else return table(def.getDbSchema(), def.getDbTable());
    }

    protected void _appendFrom(SQLDataSetDef def, Select _query) {
        if (!StringUtils.isBlank(def.getDbSQL())) _query.from(def.getDbSQL());
        else _query.from(_createTable(def));
    }

    protected void _appendFilterBy(SQLDataSetDef def, DataSetFilter filterOp, Select _query) {
        List<ColumnFilter> filterList = filterOp.getColumnFilterList();
        for (ColumnFilter filter : filterList) {
            _appendFilterBy(def, filter, _query);
        }
    }

    protected void _appendFilterBy(SQLDataSetDef def, ColumnFilter filter, Select _query) {
        Condition condition = _createCondition(def, filter);
        if (condition != null) {
            _query.where(condition);
        }
    }

    protected Condition _createCondition(SQLDataSetDef def, ColumnFilter filter) {
        String filterId = filter.getColumnId();
        Column _column = column(filterId);

        if (filter instanceof CoreFunctionFilter) {
            CoreFunctionFilter f = (CoreFunctionFilter) filter;
            CoreFunctionType type = f.getType();
            List params = f.getParameters();

            if (CoreFunctionType.IS_NULL.equals(type)) {
                return _column.isNull();
            }
            if (CoreFunctionType.NOT_NULL.equals(type)) {
                return _column.notNull();
            }
            if (CoreFunctionType.EQUALS_TO.equals(type)) {
                if (params.size() == 1) {
                    return _column.equalsTo(params.get(0));
                }
                return _column.in(params);
            }
            if (CoreFunctionType.NOT_EQUALS_TO.equals(type)) {
                if (params.size() == 1) {
                    return _column.notEquals(params.get(0));
                }
                return _column.in(params).not();
            }
            if (CoreFunctionType.LIKE_TO.equals(type)) {
                String pattern = (String) params.get(0);
                boolean caseSensitive = params.size() < 2 || Boolean.parseBoolean(params.get(1).toString());
                if (caseSensitive) {
                    return _column.like(pattern);
                } else {
                    return _column.lower().like(pattern.toLowerCase());
                }
            }
            if (CoreFunctionType.LOWER_THAN.equals(type)) {
                return _column.lowerThan(params.get(0));
            }
            if (CoreFunctionType.LOWER_OR_EQUALS_TO.equals(type)) {
                return _column.lowerOrEquals(params.get(0));
            }
            if (CoreFunctionType.GREATER_THAN.equals(type)) {
                return _column.greaterThan(params.get(0));
            }
            if (CoreFunctionType.GREATER_OR_EQUALS_TO.equals(type)) {
                return _column.greaterOrEquals(params.get(0));
            }
            if (CoreFunctionType.BETWEEN.equals(type)) {
                return _column.between(params.get(0), params.get(1));
            }
            if (CoreFunctionType.TIME_FRAME.equals(type)) {
                TimeFrame timeFrame = TimeFrame.parse(params.get(0).toString());
                if (timeFrame != null) {
                    java.sql.Date past = new java.sql.Date(timeFrame.getFrom().getTimeInstant().getTime());
                    java.sql.Date future = new java.sql.Date(timeFrame.getTo().getTimeInstant().getTime());
                    return _column.between(past, future);
                }
            }
        }
        if (filter instanceof LogicalExprFilter) {
            LogicalExprFilter f = (LogicalExprFilter) filter;
            LogicalExprType type = f.getLogicalOperator();

            Condition condition = null;
            List<ColumnFilter> logicalTerms = f.getLogicalTerms();
            for (int i=0; i<logicalTerms.size(); i++) {
                Condition next = _createCondition(def, logicalTerms.get(i));

                if (LogicalExprType.AND.equals(type)) {
                    if (condition == null) condition = next;
                    else condition = condition.and(next);
                }
                if (LogicalExprType.OR.equals(type)) {
                    if (condition == null) condition = next;
                    else condition = condition.or(next);
                }
                if (LogicalExprType.NOT.equals(type)) {
                    if (condition == null) condition = next.not();
                    else condition = condition.and(next.not());
                }
            }
            return condition;
        }
        throw new IllegalArgumentException("Filter not supported: " + filter);
    }

    public Select logSQL(Select q) {
        String sql = q.getSQL();
        log.debug(sql);
        return q;
    }

    /**
     * Class that provides an isolated context for the processing of a single lookup request.
     */
    private class LookupProcessor {

        SQLDataSetDef def;
        DataSetLookup lookup;
        DataSetMetadata metadata;
        Select _query;
        Connection conn;
        Date[] dateLimits;
        DateIntervalType dateIntervalType;
        List<DataSetOp> postProcessingOps = new ArrayList<DataSetOp>();

        public LookupProcessor(SQLDataSetDef def, DataSetLookup lookup) {
            this.def = def;
            this.lookup = lookup;
            DataSetFilter dataSetFilter = def.getDataSetFilter();
            if (dataSetFilter != null) {
                if (lookup == null) {
                    this.lookup = new DataSetLookup(def.getUUID(), dataSetFilter);
                } else {
                    this.lookup.addOperation(dataSetFilter);
                }
            }
        }

        public DataSet run() throws Exception {
            DataSource ds = dataSourceLocator.lookup(def);
            conn = ds.getConnection();
            try {
                metadata = _getDataSetMetadata(def, conn, lookup.testMode());
                int totalRows = metadata.getNumberOfRows();
                boolean trim = (lookup != null && (lookup.getNumberOfRows() > 0 || lookup.getRowOffset() > 0));

                // The whole data set
                if (lookup == null || lookup.getOperationList().isEmpty()) {

                    // Prepare the select
                    _query = select(conn).columns(_createAllColumns());
                    _appendFrom(def, _query);

                    // Row limits
                    if (trim) {
                        totalRows = _query.fetchCount();
                        _query.limit(lookup.getNumberOfRows()).offset(lookup.getRowOffset());
                    }

                    // Fetch the results and build the data set
                    ResultSet _results = logSQL(_query).fetch();
                    List<DataColumn> columns = calculateColumns(null);
                    DataSet dataSet = _buildDataSet(columns, _results);
                    if (trim) {
                        dataSet.setRowCountNonTrimmed(totalRows);
                    }
                    return dataSet;
                }
                // ... or a list of operations.
                else {
                    DataSetGroup groupOp = null;
                    int groupIdx = lookup.getFirstGroupOpIndex(0, null, false);
                    if (groupIdx != -1) groupOp = lookup.getOperation(groupIdx);

                    // Prepare the select
                    _query = select(conn).columns(_createColumns(groupOp));
                    _appendFrom(def, _query);

                    // Append the filter clauses
                    for (DataSetFilter filterOp : lookup.getOperationList(DataSetFilter.class)) {
                        _appendFilterBy(def, filterOp, _query);
                    }

                    // Append the interval selections
                    List<DataSetGroup> intervalSelects = lookup.getFirstGroupOpSelections();
                    for (DataSetGroup intervalSelect : intervalSelects) {
                        _appendIntervalSelection(intervalSelect, _query);
                    }

                    // ... the group by clauses
                    ColumnGroup cg = null;
                    if (groupOp != null) {
                        cg = groupOp.getColumnGroup();
                        if (cg != null) {
                            _appendGroupBy(groupOp);
                        }
                    }

                    // ... the sort clauses
                    DataSetSort sortOp = lookup.getFirstSortOp();
                    if (sortOp != null) {
                        if (cg != null) {
                            _appendOrderGroupBy(groupOp, sortOp);
                        } else {
                            _appendOrderBy(sortOp);
                        }
                    } else if (cg != null) {
                        _appendOrderGroupBy(groupOp);
                    }

                    // ... and the row limits
                    if (trim) {
                        totalRows = _query.fetchCount();
                        _query.limit(lookup.getNumberOfRows()).offset(lookup.getRowOffset());
                    }

                    // Fetch the results and build the data set
                    ResultSet _results = logSQL(_query).fetch();
                    List<DataColumn> columns = calculateColumns(groupOp);
                    DataSet dataSet = _buildDataSet(columns, _results);
                    if (trim) dataSet.setRowCountNonTrimmed(totalRows);
                    return dataSet;
                }
            } finally {
                conn.close();
            }
        }

        protected DateIntervalType calculateDateInterval(ColumnGroup cg) {
            if (dateIntervalType != null) {
                return dateIntervalType;
            }

            if (GroupStrategy.DYNAMIC.equals(cg.getStrategy())) {
                Date[] limits = calculateDateLimits(cg.getSourceId());
                if (limits != null) {
                    dateIntervalType = intervalBuilderDynamicDate.calculateIntervalSize(limits[0], limits[1], cg);
                    return dateIntervalType;
                }
            }
            dateIntervalType = DateIntervalType.getByName(cg.getIntervalSize());
            return dateIntervalType;
        }

        protected Date[] calculateDateLimits(String dateColumnId) {
            if (dateLimits != null) return dateLimits;

            Date minDate = calculateDateLimit(dateColumnId, true);
            Date maxDate = calculateDateLimit(dateColumnId, false);
            return dateLimits = new Date[] {minDate, maxDate};
        }

        protected Date calculateDateLimit(String dateColumnId, boolean min) {

            Column _dateColumn = column(dateColumnId);
            Select _limitsQuery = select(conn).columns(_dateColumn);
            _appendFrom(def, _limitsQuery);

            // Append the filter clauses
            for (DataSetFilter filterOp : lookup.getOperationList(DataSetFilter.class)) {
                _appendFilterBy(def, filterOp, _limitsQuery);
            }

            // Append group interval selection filters
            List<DataSetGroup> intervalSelects = lookup.getFirstGroupOpSelections();
            for (DataSetGroup intervalSelect : intervalSelects) {
                _appendIntervalSelection(intervalSelect, _limitsQuery);
            }

            try {
                // Fetch the date
                ResultSet rs = logSQL(_limitsQuery
                        .where(_dateColumn.notNull())
                        .orderBy(min ? _dateColumn.asc() : _dateColumn.desc())
                        .limit(1)).fetch();

                if (!rs.next()) {
                    return null;
                } else {
                    return rs.getDate(1);
                }
            } catch (SQLException e) {
                log.error("Error reading date limit from query results", e);
                return null;
            }
        }

        protected List<DataColumn> calculateColumns(DataSetGroup gOp) {
            List<DataColumn> result = new ArrayList<DataColumn>();

            if (gOp == null) {
                for (int i = 0; i < metadata.getNumberOfColumns(); i++) {
                    String columnId = metadata.getColumnId(i);
                    ColumnType columnType = metadata.getColumnType(i);
                    DataColumn column = new DataColumnImpl(columnId, columnType);
                    result.add(column);
                }
            }
            else {
                ColumnGroup cg = gOp.getColumnGroup();
                for (GroupFunction gf : gOp.getGroupFunctions()) {

                    String sourceId = gf.getSourceId();
                    String columnId = _getTargetColumnId(gf);

                    DataColumnImpl column = new DataColumnImpl();
                    column.setId(columnId);
                    column.setGroupFunction(gf);
                    result.add(column);

                    // Group column
                    if (cg != null && cg.getSourceId().equals(sourceId) && gf.getFunction() == null) {
                        column.setColumnType(ColumnType.LABEL);
                        column.setColumnGroup(cg);
                        if (ColumnType.DATE.equals(_getColumnType(sourceId))) {
                            column.setIntervalType(dateIntervalType != null ? dateIntervalType.toString() : null);
                            column.setMinValue(dateLimits != null ? dateLimits[0] : null);
                            column.setMaxValue(dateLimits != null ? dateLimits[1] : null);
                        }
                    }
                    // Aggregated column
                    else if (gf.getFunction() != null) {
                        column.setColumnType(ColumnType.NUMBER);
                    }
                    // Existing Column
                    else {
                        column.setColumnType(_getColumnType(sourceId));
                    }
                }
            }
            return result;
        }

        protected void _appendOrderBy(DataSetSort sortOp) {
            List<SortColumn> _columns = new ArrayList<SortColumn>();
            List<ColumnSort> sortList = sortOp.getColumnSortList();
            for (ColumnSort columnSort : sortList) {
                String columnId = columnSort.getColumnId();
                _assertColumnExists(columnId);

                if (SortOrder.DESCENDING.equals(columnSort.getOrder())) {
                    _columns.add(column(columnId).desc());
                } else {
                    _columns.add(column(columnId).asc());
                }
            }
            _query.orderBy(_columns);
        }

        protected boolean isDynamicDateGroup(DataSetGroup groupOp) {
            ColumnGroup cg = groupOp.getColumnGroup();
            if (!ColumnType.DATE.equals(_getColumnType(cg.getSourceId()))) return false;
            if (!GroupStrategy.DYNAMIC.equals(cg.getStrategy())) return false;
            return true;
        }

        protected void _appendOrderGroupBy(DataSetGroup groupOp) {
            if (isDynamicDateGroup(groupOp)) {
                ColumnGroup cg = groupOp.getColumnGroup();
                _query.orderBy(_createColumn(cg).asc());

                // If the group column is in the resulting data set then ensure the data set order
                GroupFunction gf = groupOp.getGroupFunction(cg.getSourceId());
                if (gf != null) {
                    DataSetSort sortOp = new DataSetSort();
                    String targetId = _getTargetColumnId(gf);
                    sortOp.addSortColumn(new ColumnSort(targetId, SortOrder.ASCENDING));
                    postProcessingOps.add(sortOp);
                }
            }
        }

        protected void _appendOrderGroupBy(DataSetGroup groupOp, DataSetSort sortOp) {
            List<SortColumn> _columns = new ArrayList<SortColumn>();
            List<ColumnSort> sortList = sortOp.getColumnSortList();
            ColumnGroup cg = groupOp.getColumnGroup();

            for (ColumnSort cs : sortList) {
                GroupFunction gf = groupOp.getGroupFunction(cs.getColumnId());

                // Sort by the group column
                if (cg.getSourceId().equals(cs.getColumnId()) || cg.getColumnId().equals(cs.getColumnId())) {
                    if (SortOrder.DESCENDING.equals(cs.getOrder())) {
                        _columns.add(_createColumn(cg).desc());
                        if (isDynamicDateGroup(groupOp)) {
                            postProcessingOps.add(sortOp);
                        }
                    } else {
                        _columns.add(_createColumn(cg).asc());
                        if (isDynamicDateGroup(groupOp)) {
                            postProcessingOps.add(sortOp);
                        }
                    }
                }
                // Sort by an aggregation
                else if (gf != null) {
                    // In SQL, sort is only permitted for columns belonging to the result set.
                    if (SortOrder.DESCENDING.equals(cs.getOrder())) {
                        _columns.add(_createColumn(gf).desc());
                    } else {
                        _columns.add(_createColumn(gf).asc());
                    }
                }
            }
            _query.orderBy(_columns);
        }

        protected void _appendIntervalSelection(DataSetGroup intervalSel, Select _query) {
            if (intervalSel != null && intervalSel.isSelect()) {
                ColumnGroup cg = intervalSel.getColumnGroup();
                List<Interval> intervalList = intervalSel.getSelectedIntervalList();

                // Get the filter values
                List<Comparable> names = new ArrayList<Comparable>();
                Comparable min = null;
                Comparable max = null;
                for (Interval interval : intervalList) {
                    names.add(interval.getName());
                    Comparable intervalMin = (Comparable) interval.getMinValue();
                    Comparable intervalMax = (Comparable) interval.getMaxValue();

                    if (intervalMin != null) {
                        if (min == null) min = intervalMin;
                        else if (min.compareTo(intervalMin) > 0) min = intervalMin;
                    }
                    if (intervalMax != null) {
                        if (max == null) max = intervalMax;
                        else if (max.compareTo(intervalMax) > 0) max = intervalMax;
                    }
                }
                // Min can't be greater than max.
                if (min != null && max != null && min.compareTo(max) > 0) {
                    min = max;
                }

                // Apply the filter
                ColumnFilter filter = null;
                if (min != null && max != null) {
                    filter = FilterFactory.between(cg.getSourceId(), min, max);
                }
                else if (min != null) {
                    filter = FilterFactory.greaterOrEqualsTo(cg.getSourceId(), min);
                }
                else if (max != null) {
                    filter = FilterFactory.lowerOrEqualsTo(cg.getSourceId(), max);
                }
                else {
                    filter = FilterFactory.equalsTo(cg.getSourceId(), names);
                }
                _appendFilterBy(def, filter, _query);
            }
        }

        protected void _appendGroupBy(DataSetGroup groupOp) {
            ColumnGroup cg = groupOp.getColumnGroup();
            String sourceId = cg.getSourceId();
            ColumnType columnType = metadata.getColumnType(_assertColumnExists(sourceId));
            boolean postProcessing = false;

            // Group by Number => not supported
            if (ColumnType.NUMBER.equals(columnType)) {
                throw new IllegalArgumentException("Group by number '" + sourceId + "' not supported");
            }
            // Group by Text => not supported
            if (ColumnType.TEXT.equals(columnType)) {
                throw new IllegalArgumentException("Group by text '" + sourceId + "' not supported");
            }
            // Group by Date
            else if (ColumnType.DATE.equals(columnType)) {
                _query.groupBy(_createColumn(cg));
                postProcessing = true;
            }
            // Group by Label
            else {
                _query.groupBy(column(sourceId));
                for (GroupFunction gf : groupOp.getGroupFunctions()) {
                    if (!sourceId.equals(gf.getSourceId()) && gf.getFunction() == null) {
                        postProcessing = true;
                    }
                }
            }

            // Also add any non-aggregated column (columns pick up) to the group statement
            for (GroupFunction gf : groupOp.getGroupFunctions()) {
                if (gf.getFunction() == null && !gf.getSourceId().equals(cg.getSourceId())) {
                    _query.groupBy(column(gf.getSourceId()));
                }
            }
            // The group operation might require post processing
            if (postProcessing) {
                DataSetGroup postGroup = groupOp.cloneInstance();
                GroupFunction gf = postGroup.getGroupFunction(sourceId);
                if (gf != null) {
                    String targetId = _getTargetColumnId(gf);
                    postGroup.getColumnGroup().setSourceId(targetId);
                    postGroup.getColumnGroup().setColumnId(targetId);
                }
                for (GroupFunction pgf : postGroup.getGroupFunctions()) {
                    AggregateFunctionType pft = pgf.getFunction();
                    pgf.setSourceId(_getTargetColumnId(pgf));
                    if (pft != null && (AggregateFunctionType.DISTINCT.equals(pft) || AggregateFunctionType.COUNT.equals(pft))) {
                        pgf.setFunction(AggregateFunctionType.SUM);
                    }
                }
                postProcessingOps.add(postGroup);
            }
        }

        protected DataSet _buildDataSet(List<DataColumn> columns, ResultSet _rs) throws Exception {
            DataSet dataSet = DataSetFactory.newEmptyDataSet();
            DataColumn dateGroupColumn = null;
            boolean dateIncludeEmptyIntervals = false;

            // Create an empty data set
            for (int i = 0; i < columns.size(); i++) {
                DataColumn column = columns.get(i).cloneEmpty();
                dataSet.addColumn(column);
            }

            // Offset post-processing
            if (_query.isOffsetPostProcessing() && _query.getOffset() > 0) {
                // Move the cursor to the specified offset or until the end of the result set is reached
                for (int i=0; i<_query.getOffset() && _rs.next(); i++);
            }

            // Populate the data set
            int rowIdx = 0;
            int numRows = _query.getLimit();
            while (_rs.next() && (numRows < 0 || rowIdx++ < numRows)) {
                for (int i=0; i<columns.size(); i++) {
                    DataColumn column = dataSet.getColumnByIndex(i);
                    column.getValues().add(_rs.getObject(i+1));
                }
            }

            // Process the data set values according to each column type and the JDBC dialect
            Dialect dialect = JDBCUtils.dialect(conn);
            for (DataColumn column : dataSet.getColumns()) {
                ColumnType columnType = column.getColumnType();
                List values = column.getValues();

                if (ColumnType.LABEL.equals(columnType)) {
                    ColumnGroup cg = column.getColumnGroup();
                    if (cg != null && ColumnType.DATE.equals(_getColumnType(cg.getSourceId()))) {
                        dateGroupColumn = column;
                        dateIncludeEmptyIntervals = cg.areEmptyIntervalsAllowed();

                        // If grouped by date then convert back to absolute dates
                        // in order to allow the post processing of the data set.
                        column.setColumnType(ColumnType.DATE);
                        for (int j=0; j<values.size(); j++) {
                            Object val = values.remove(j);
                            Date dateObj = DateUtils.parseDate(column, val);
                            values.add(j, dateObj);
                        }
                    }
                    else {
                        for (int j=0; j<values.size(); j++) {
                            Object value = dialect.convertToString(values.remove(j));
                            values.add(j, value);
                        }
                    }
                }
                else if (ColumnType.NUMBER.equals(columnType)) {
                    for (int j=0; j<values.size(); j++) {
                        Object value = dialect.convertToDouble(values.remove(j));
                        values.add(j, value);
                    }
                }
                else if (ColumnType.DATE.equals(columnType)) {
                    for (int j=0; j<values.size(); j++) {
                        Object value = dialect.convertToDate(values.remove(j));
                        values.add(j, value);
                    }
                }
                else {
                    for (int j=0; j<values.size(); j++) {
                        Object value = dialect.convertToString(values.remove(j));
                        values.add(j, value);
                    }
                }

                column.setValues(values);
            }
            // Some operations requires some in-memory post-processing
            if (!postProcessingOps.isEmpty()) {
                DataSet tempSet = opEngine.execute(dataSet, postProcessingOps);
                dataSet = dataSet.cloneEmpty();
                for (int i=0; i<columns.size(); i++) {
                    DataColumn source = tempSet.getColumnByIndex(i);
                    DataColumn target = dataSet.getColumnByIndex(i);
                    target.setColumnType(source.getColumnType());
                    target.setIntervalType(source.getIntervalType());
                    target.setMinValue(target.getMinValue());
                    target.setMaxValue(target.getMaxValue());
                    target.setValues(new ArrayList(source.getValues()));
                }
            }
            // Group by date might require to include empty intervals
            if (dateIncludeEmptyIntervals)  {
                IntervalBuilder intervalBuilder = intervalBuilderLocator.lookup(ColumnType.DATE, dateGroupColumn.getColumnGroup().getStrategy());
                IntervalList intervalList = intervalBuilder.build(dateGroupColumn);
                if (intervalList.size() > dataSet.getRowCount()) {
                    List values = dateGroupColumn.getValues();
                    int valueIdx = 0;

                    for (int intervalIdx = 0; intervalIdx < intervalList.size(); intervalIdx++) {
                        String interval = intervalList.get(intervalIdx).getName();
                        String value = values.isEmpty() ? null : (String) values.get(valueIdx++);
                        if (value == null || !value.equals(interval)) {
                            dataSet.addEmptyRowAt(intervalIdx);
                            dateGroupColumn.getValues().set(intervalIdx, interval);
                        }
                    }
                }
            }
            return dataSet;
        }

        protected Collection<Column> _createAllColumns() {
            Collection<Column> columns = new ArrayList<Column>();
            for (int i = 0; i < metadata.getNumberOfColumns(); i++) {
                String columnId = metadata.getColumnId(i);
                columns.add(column(columnId));
            }
            return columns;
        }

        protected Collection<Column> _createColumns(DataSetGroup gOp) {
            if (gOp == null) {
                return _createAllColumns();
            }

            ColumnGroup cg = gOp.getColumnGroup();
            Collection<Column> _columns = new ArrayList<Column>();
            for (GroupFunction gf : gOp.getGroupFunctions()) {

                String sourceId = gf.getSourceId();
                String targetId = gf.getColumnId();
                if (sourceId == null) {
                    sourceId = metadata.getColumnId(0);
                } else {
                    _assertColumnExists(sourceId);
                }

                if (cg != null && cg.getSourceId().equals(sourceId) && gf.getFunction() == null) {
                    _columns.add(_createColumn(cg).as(targetId));
                } else {
                    _columns.add(_createColumn(gf).as(targetId));
                }
            }
            return _columns;
        }

        protected Column _createColumn(GroupFunction gf) {
            String sourceId = gf.getSourceId();
            if (sourceId == null) {
                sourceId = metadata.getColumnId(0);
            }

            AggregateFunctionType ft = gf.getFunction();
            return column(sourceId).function(ft);
        }

        protected Column _createColumn(ColumnGroup cg) {
            String columnId = cg.getSourceId();
            _assertColumnExists(columnId);

            ColumnType type = _getColumnType(columnId);

            if (ColumnType.DATE.equals(type)) {
                DateIntervalType size = calculateDateInterval(cg);
                return column(cg.getSourceId(), cg.getStrategy(), size);
            }
            if (ColumnType.NUMBER.equals(type)) {
                throw new IllegalArgumentException("Group by number '" + columnId + "' not supported");
            }
            if (ColumnType.TEXT.equals(type)) {
                throw new IllegalArgumentException("Group by text '" + columnId + "' not supported");
            }
            return column(columnId);
        }


        protected int _assertColumnExists(String columnId) {
            String targetId = JDBCUtils.changeCase(conn, columnId);
            for (int i = 0; i < metadata.getNumberOfColumns(); i++) {
                if (metadata.getColumnId(i).equals(targetId)) {
                    return i;
                }
            }
            throw new RuntimeException("Column '" + columnId +
                    "' not found in data set: " + metadata.getUUID());
        }

        protected String _getTargetColumnId(GroupFunction gf) {
            String sourceId = gf.getSourceId();
            if (sourceId != null) _assertColumnExists(sourceId);
            return gf.getColumnId() == null ?  sourceId : gf.getColumnId();
        }

        protected ColumnType _getColumnType(String columnId) {
            String _col = JDBCUtils.changeCase(conn, columnId);
            return metadata.getColumnType(_col);
        }
    }
}
