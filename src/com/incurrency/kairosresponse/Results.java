/*
 * Copyright 2013 Proofpoint Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package com.incurrency.kairosresponse;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Query Results. This is the results of a single query.
 */
public class Results {

    private String name;
    private Map<String, List<String>> tags;

    @SerializedName("values")
    private List<List<Object>> dataPoints;

    @SerializedName("group_by")
    private List<GroupResult> groupResults;

    public Results(String name,
            Map<String, List<String>> tags,
            List<List<Object>> dataPoints,
            List<GroupResult> groupResults) {
        this.name = name;
        this.tags = tags;
        this.groupResults = groupResults;
        this.dataPoints = dataPoints;
    }

    public String getName() {
        return name;
    }

    public List<List<Object>> getDataPoints() {
        return dataPoints;
    }

    public Map<String, List<String>> getTags() {
        return tags;
    }

    public List<GroupResult> getGroupResults() {
        return groupResults;
    }

    /**
     * @param dataPoints the dataPoints to set
     */
    public void setDataPoints(List<List<Object>> dataPoints) {
        this.dataPoints = dataPoints;
    }
}
