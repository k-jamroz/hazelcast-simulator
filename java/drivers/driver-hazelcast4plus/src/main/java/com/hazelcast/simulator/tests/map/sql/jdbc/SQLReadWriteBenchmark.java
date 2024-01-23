package com.hazelcast.simulator.tests.map.sql.jdbc;

import com.hazelcast.map.IMap;
import com.hazelcast.simulator.hz.HazelcastTest;
import com.hazelcast.simulator.test.annotations.Prepare;
import com.hazelcast.simulator.test.annotations.Setup;
import com.hazelcast.simulator.test.annotations.Teardown;
import com.hazelcast.simulator.test.annotations.TimeStep;
import com.hazelcast.simulator.test.annotations.Verify;
import com.hazelcast.simulator.tests.helpers.KeyLocality;
import com.hazelcast.simulator.worker.loadsupport.Streamer;
import com.hazelcast.simulator.worker.loadsupport.StreamerFactory;
import com.hazelcast.sql.SqlResult;
import com.hazelcast.sql.SqlRow;
import com.hazelcast.sql.SqlService;
import com.hazelcast.sql.impl.client.SqlClientService;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static com.hazelcast.simulator.tests.helpers.KeyUtils.generateIntKeys;
import static com.hazelcast.simulator.tests.helpers.KeyUtils.generateStringKeys;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SQLReadWriteBenchmark extends HazelcastTest {

    private static final String TEST_DATABASE_REF = "sqlitedc";
    private static final String litePrefix = "LITE!" + TEST_DATABASE_REF + "!";


    // properties
    // the number of map entries
    public int entryCount = 10_000_000;

    //16 byte + N*(20*N
    public int arraySize = 20;
    private String sampleArray;
    private String otherArray;
    public String jdbcUrl;
    public int maximumPoolSize = 10;
    public boolean useLite = false;

    // fields
    private String mapSelectQuery;
    private String mapUpdateQuery;
    private String selectQuery;
    private String updateQuery;
    private SqlService sqlService;
    private IMap<Integer, String> map;

    @Setup
    public void setUp() {
        sampleArray = "1".repeat(arraySize);
        otherArray = "2".repeat(arraySize);
        sqlService = targetInstance.getSql();
        map = targetInstance.getMap("map" + name);
        mapSelectQuery = "select this from map" + name + " where __key=?";
        mapUpdateQuery = "update map" + name + " set this = ? where __key=?";
        selectQuery = (useLite ? litePrefix : "") + "SELECT name FROM " + name + " WHERE id = ?";
        updateQuery = (useLite ? litePrefix : "") + "UPDATE " + name + " SET name = ? WHERE id = ?";
    }

    @Prepare(global = true)
    public void prepare() {
        if (name.startsWith("imap")) {
            // IMap
            Streamer<Integer, String> streamer = StreamerFactory.getInstance(map);
            for (int i = 0; i < entryCount; ++i) {
                streamer.pushEntry(i, sampleArray);
            }
            streamer.await();

            String mapQuery = String.format("CREATE MAPPING IF NOT EXISTS map%s " +
                            "TYPE IMap " +
                            "OPTIONS (" +
                            "    'keyFormat'='int'," +
                            "    'valueFormat'='varchar'" +
                            ")",
                    name
            );
            sqlService.executeUpdate(mapQuery);
        } else {
            // db
            sqlService.executeUpdate(String.format("CREATE DATA CONNECTION %s TYPE JDBC OPTIONS('jdbcUrl'='%s', 'maximumPoolSize'='%d')",
                    TEST_DATABASE_REF, Objects.requireNonNull(jdbcUrl), maximumPoolSize));

            sqlService.executeUpdate(litePrefix +
                    "CREATE TABLE " + name + " (" +
                    "id INT PRIMARY KEY, name VARCHAR(100)" +
                    ")");

            String query = String.format("CREATE MAPPING IF NOT EXISTS %s "
                            + "        DATA CONNECTION \"%s\""
                            + " OPTIONS('maximumPoolSize'='%d')",
                    name, TEST_DATABASE_REF, maximumPoolSize
            );
            sqlService.executeUpdate(query);

            String insertData = String.format("insert into %s select v, ? from table(generate_series(0, ?))", name);
            sqlService.executeUpdate(insertData, sampleArray, entryCount - 1);
        }
    }

    // almost the simplest SQL query - returns 1 row without using Jet job
    // (communication + query threads + some SQL overhead (plan is cached) + partition threads)
    @TimeStep(prob = 0)
    public void imapSelect() {
        int actual = 0;
        int key = ThreadLocalRandom.current().nextInt(entryCount);
        try (SqlResult result = sqlService.execute(mapSelectQuery, key)) {
            for (SqlRow row : result) {
                Object value = row.getObject(0);
                assertEquals(sampleArray, value);
                actual++;
            }
        }

        if (actual != 1) {
            throw new IllegalArgumentException("Invalid count [expected=" + 1 + ", actual=" + actual + "]");
        }
    }

    @TimeStep(prob = 0)
    public void imapUpdate() {
        int key = ThreadLocalRandom.current().nextInt(entryCount);
        try (SqlResult result = sqlService.execute(mapUpdateQuery, otherArray, key)) {
        }
    }

    // direct IMap operation (communication + partition threads)
    @TimeStep(prob = 0)
    public void imapGet() {
        int key = ThreadLocalRandom.current().nextInt(entryCount);
        assertEquals(sampleArray, map.get(key));
    }

    // JDBC connector or passthrough
    @TimeStep(prob = 0)
    public void select() throws Exception {
        int key = ThreadLocalRandom.current().nextInt(entryCount);
        int actual = 0;
        try (SqlResult result = sqlService.execute(selectQuery, key)) {
            for (SqlRow row : result) {
                Object value = row.getObject(0);
                assertEquals(arraySize, ((String) value).length());
                actual++;
            }
        }

        if (actual != 1) {
            throw new IllegalArgumentException("Invalid count [expected=" + 1 + ", actual=" + actual + "]");
        }
    }

    @TimeStep(prob = 0)
    public void update() {
        int key = ThreadLocalRandom.current().nextInt(entryCount);
        try (SqlResult result = sqlService.execute(updateQuery, otherArray, key)) {
        }
    }
}
