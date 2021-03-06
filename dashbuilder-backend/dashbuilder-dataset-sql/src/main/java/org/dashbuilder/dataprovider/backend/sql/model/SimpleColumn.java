/**
 * Copyright (C) 2015 JBoss Inc
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
package org.dashbuilder.dataprovider.backend.sql.model;

import java.util.ArrayList;
import java.util.Collection;

import org.dashbuilder.dataset.ColumnType;
import org.dashbuilder.dataset.filter.CoreFunctionType;
import org.dashbuilder.dataset.filter.LogicalExprType;
import org.dashbuilder.dataset.group.AggregateFunctionType;
import org.dashbuilder.dataset.sort.SortOrder;

public class SimpleColumn extends Column {

    protected AggregateFunctionType functionType;

    public SimpleColumn(String name) {
        super(name);
    }

    public SimpleColumn(String name, ColumnType type, int length) {
        super(name);
        this.type = type;
        this.length = length;
    }

    public AggregateFunctionType getFunctionType() {
        return functionType;
    }

    public void setFunctionType(AggregateFunctionType functionType) {
        this.functionType = functionType;
    }
}
