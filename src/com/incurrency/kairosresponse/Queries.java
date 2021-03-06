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

/**
 * Resulting object from a Query.
 */
public class Queries {

    private List<Results> results;

    @SerializedName("sample_size")
    private long sampleSize;

    public Queries(List<Results> results, long sampleSize) {
        this.results = results;
        this.sampleSize = sampleSize;
    }

    public List<Results> getResults() {
        return results;
    }

    /**
     * Returns the number of data points returned by the query prior to
     * aggregation. Aggregation by reduce the number of data points actually
     * returned.
     *
     * @return number of data points returned by the query
     */
    public long getSampleSize() {
        return sampleSize;
    }
}
