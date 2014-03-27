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
package org.dashbuilder.client.js;

import java.util.List;
import java.util.ArrayList;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import org.dashbuilder.client.dataset.DataColumn;
import org.dashbuilder.client.dataset.DataSet;

/**
 * {
 *  "columns": [
 *      {
 *          "id": "department",
 *          "type": "label",
 *          "name": "Department",
 *          "values": {"sales", engineering", "administration", "HR"}
 *      },
 *      {
 *          "id": "amount",
 *          "type": "label",
 *          "name": "Total expenses amount",
 *          "values": {"10300.45", "9000.00", "3022.44", "22223.56"}
 *      }
 *  ]
 * }
 */
public class JsDataSet extends JavaScriptObject implements DataSet {

    // Overlay types always have protected, zero-arg constructors
    protected JsDataSet() {}

    public static native JsDataSet fromJson(String jsonString) /*-{
        return eval('(' + jsonString + ')');
    }-*/;

    private final native JsArray<JsDataColumn> getJSColumns() /*-{
        return this.columns;
    }-*/;

    public final List<DataColumn> getColumns() {
        List<DataColumn> results = new ArrayList<DataColumn>();
        JsArray<JsDataColumn> array = getJSColumns();
        for (int i = 0; i < array.length(); i++) {
            results.add(array.get(i));
        }
        return results;
    }
}