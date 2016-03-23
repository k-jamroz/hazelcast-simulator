/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hazelcast.simulator.tests.map;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.simulator.test.TestContext;
import com.hazelcast.simulator.test.TestRunner;
import com.hazelcast.simulator.test.annotations.RunWithWorker;
import com.hazelcast.simulator.test.annotations.Setup;
import com.hazelcast.simulator.test.annotations.Teardown;
import com.hazelcast.simulator.worker.tasks.AbstractMonotonicWorker;

import java.util.SortedMap;
import java.util.TreeMap;

import static com.hazelcast.simulator.tests.helpers.HazelcastTestUtils.getOperationCountInformation;

/**
 * Test for {@link IMap#putAll(java.util.Map)} which creates the input values on the fly during the RUN phase.
 *
 * You can configure the {@link #batchSize} to determine the number of inserted values per operation.
 * You can configure the {@link #keyRange} to determine the key range for inserted values.
 */
public class MapPutAllOnTheFlyTest {

    private static final ILogger LOGGER = Logger.getLogger(MapPutAllOnTheFlyTest.class);

    // properties
    public String basename = MapPutAllOnTheFlyTest.class.getSimpleName();
    public int batchSize = 10;
    public int keyRange = 1000000;

    private HazelcastInstance targetInstance;
    private IMap<Integer, Integer> map;

    @Setup
    public void setUp(TestContext testContext) {
        targetInstance = testContext.getTargetInstance();
        map = targetInstance.getMap(basename);
    }

    @Teardown
    public void tearDown() {
        map.destroy();
        LOGGER.info(getOperationCountInformation(targetInstance));
    }

    @RunWithWorker
    public Worker createWorker() {
        return new Worker();
    }

    private class Worker extends AbstractMonotonicWorker {

        @Override
        protected void timeStep() throws Exception {
            SortedMap<Integer, Integer> values = new TreeMap<Integer, Integer>();
            for (int i = 0; i < batchSize; i++) {
                int key = randomInt(keyRange);

                values.put(key, key);
            }

            map.putAll(values);
        }
    }

    public static void main(String[] args) throws Exception {
        MapPutAllOnTheFlyTest test = new MapPutAllOnTheFlyTest();
        new TestRunner<MapPutAllOnTheFlyTest>(test).withDuration(10).run();
    }
}
