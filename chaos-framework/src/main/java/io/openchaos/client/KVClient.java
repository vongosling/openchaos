/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.openchaos.client;

import io.openchaos.recorder.ResponseLogEntry;
import java.util.List;
import io.openchaos.common.InvokeResult;
import io.openchaos.driver.kv.KVDriver;
import io.openchaos.generator.Operation;
import io.openchaos.generator.SequenceGenerator;
import io.openchaos.recorder.Recorder;
import io.openchaos.recorder.RequestLogEntry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KVClient implements Client {

    private static final AtomicInteger CLIENT_ID_GENERATOR = new AtomicInteger(0);
    private static final Logger log = LoggerFactory.getLogger(io.openchaos.client.KVClient.class);

    private io.openchaos.driver.kv.KVClient client;
    private KVDriver driver;
    private Recorder recorder;
    private int clientId;

    private Optional<String> key;

    public KVClient(KVDriver driver, Recorder recorder, Optional<String> key) {
        this.driver = driver;
        this.recorder = recorder;
        this.clientId = CLIENT_ID_GENERATOR.getAndIncrement();
        this.key = key;
    }

    @Override public void setup() {
        if (driver == null) {
            throw new IllegalArgumentException("cacheChaosDriver is null when setup CacheClient");
        }
        client = driver.createClient();
        client.start();
    }

    @Override public void teardown() {
        client.close();
    }

    @Override public void nextInvoke() {
        Operation op = SequenceGenerator.generateCacheOperation();
        RequestLogEntry requestLogEntry = new RequestLogEntry(clientId, op.getInvokeOperation(), op.getValue(), System.currentTimeMillis());
        recorder.recordRequest(requestLogEntry);
        InvokeResult result = client.put(key, op.getValue());
        recorder.recordResponse(new ResponseLogEntry(clientId, op.getInvokeOperation(), result, op.getValue(), System.currentTimeMillis(), System.currentTimeMillis() - requestLogEntry.timestamp));
    }

    @Override public void lastInvoke() {
        RequestLogEntry requestLogEntry = new RequestLogEntry(clientId, "getAll", null, System.currentTimeMillis());
        recorder.recordRequest(requestLogEntry);
        List<String> results = client.getAll(key);
        if (results != null && !results.isEmpty()) {
            recorder.recordResponse(new ResponseLogEntry(clientId, "getAll", InvokeResult.SUCCESS, results.toString(), System.currentTimeMillis(), System.currentTimeMillis() - requestLogEntry.timestamp));
        } else {
            recorder.recordResponse(new ResponseLogEntry(clientId, "getAll", InvokeResult.FAILURE, null, System.currentTimeMillis(), System.currentTimeMillis() - requestLogEntry.timestamp));
        }
    }
}
