/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */
 
/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.opensearch.spark.integration;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.opensearch.hadoop.rest.RestUtils;
import org.opensearch.hadoop.util.OpenSearchMajorVersion;
import org.opensearch.hadoop.util.TestSettings;
import org.opensearch.hadoop.util.TestUtils;
import org.opensearch.spark.rdd.Metadata;
import org.opensearch.spark.rdd.api.java.JavaOpenSearchSpark;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import static org.opensearch.hadoop.util.TestUtils.docEndpoint;
import static org.opensearch.hadoop.util.TestUtils.resource;
import static org.junit.Assert.*;

import static org.opensearch.hadoop.cfg.ConfigurationOptions.*;
import static org.opensearch.spark.rdd.Metadata.*;

import static org.hamcrest.Matchers.*;
import static org.opensearch.spark.rdd.api.java.JavaOpenSearchSpark.saveToOpenSearch;

import scala.Tuple2;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AbstractJavaOpenSearchSparkTest implements Serializable {

    private static final transient SparkConf conf = new SparkConf()
                    .setAll(ScalaUtils.propertiesAsScalaMap(TestSettings.TESTING_PROPS))
                    .set("spark.io.compression.codec", "lz4")
                    .setAppName("opensearchtest");
    private static transient JavaSparkContext sc = null;

    private final OpenSearchMajorVersion version = TestUtils.getOpenSearchClusterInfo().getMajorVersion();

    @BeforeClass
    public static void setup() {
        sc = new JavaSparkContext(conf);
    }

    @AfterClass
    public static void clean() throws Exception {
        if (sc != null) {
            sc.stop();
            // wait for jetty & spark to properly shutdown
            Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        }
    }

    // @Test
    public void testOpenSearchRDDWrite() throws Exception {
        Map<String, ?> doc1 = ImmutableMap.of("one", 1, "two", 2);
        Map<String, ?> doc2 = ImmutableMap.of("OTP", "Otopeni", "SFO", "San Fran");

        String target = "spark-test-java-basic-write/data";
        JavaRDD<Map<String, ?>> javaRDD = sc.parallelize(ImmutableList.of(doc1, doc2));
        // eliminate with static import
        JavaOpenSearchSpark.saveToOpenSearch(javaRDD, target);
        JavaOpenSearchSpark.saveToOpenSearch(javaRDD, ImmutableMap.of(OPENSEARCH_RESOURCE, target + "1"));

        assertEquals(2, JavaOpenSearchSpark.opensearchRDD(sc, target).count());
        assertTrue(RestUtils.exists(target));
        String results = RestUtils.get(target + "/_search?");
        assertThat(results, containsString("SFO"));
    }

    // @Test
    public void testOpenSearchRDDWriteWithMappingId() throws Exception {
        Map<String, ?> doc1 = ImmutableMap.of("one", 1, "two", 2, "number", 1);
        Map<String, ?> doc2 = ImmutableMap.of("OTP", "Otopeni", "SFO", "San Fran", "number", 2);

        String target = "spark-test-java-id-write/data";
        JavaRDD<Map<String, ?>> javaRDD = sc.parallelize(ImmutableList.of(doc1, doc2));
        // eliminate with static import
        JavaOpenSearchSpark.saveToOpenSearch(javaRDD, target, ImmutableMap.of(OPENSEARCH_MAPPING_ID, "number"));

        assertEquals(2, JavaOpenSearchSpark.opensearchRDD(sc, target).count());
        assertTrue(RestUtils.exists(target + "/1"));
        assertTrue(RestUtils.exists(target + "/2"));
        String results = RestUtils.get(target + "/_search?");
        assertThat(results, containsString("SFO"));
    }

    // @Test
    public void testOpenSearchRDDWriteWithDynamicMapping() throws Exception {
        Map<String, ?> doc1 = ImmutableMap.of("one", 1, "two", 2, "number", 1);
        Map<String, ?> doc2 = ImmutableMap.of("OTP", "Otopeni", "SFO", "San Fran", "number", 2);

        String target = "spark-test-java-dyn-id-write/data";
        JavaPairRDD<?, ?> pairRdd = sc.parallelizePairs(ImmutableList.of(new Tuple2<Object,Object>(1, doc1),
                new Tuple2<Object, Object>(2, doc2)));
        //JavaPairRDD pairRDD = JavaPairRDD.fromJavaRDD(tupleRdd);
        // eliminate with static import
        JavaOpenSearchSpark.saveToOpenSearchWithMeta(pairRdd, target);

        assertEquals(2, JavaOpenSearchSpark.opensearchRDD(sc, target).count());
        assertTrue(RestUtils.exists(target + "/1"));
        assertTrue(RestUtils.exists(target + "/2"));
        String results = RestUtils.get(target + "/_search?");
        assertThat(results, containsString("SFO"));
    }

    // @Test
    public void testOpenSearchRDDWriteWithDynamicMappingBasedOnMaps() throws Exception {
        Map<String, ?> doc1 = ImmutableMap.of("one", 1, "two", 2, "number", 1);
        Map<String, ?> doc2 = ImmutableMap.of("OTP", "Otopeni", "SFO", "San Fran", "number", 2);

        String target = "spark-test-java-dyn-map-id-write/data";
        Map<Metadata, Object> header1 = ImmutableMap.<Metadata, Object> of(ID, 1, TTL, "1d");
        Map<Metadata, Object> header2 = ImmutableMap.<Metadata, Object> of(ID, "2", TTL, "2d");
        JavaRDD<Tuple2<Object, Object>> tupleRdd = sc.parallelize(ImmutableList.<Tuple2<Object, Object>> of(new Tuple2(header1, doc1), new Tuple2(header2, doc2)));
        JavaPairRDD pairRDD = JavaPairRDD.fromJavaRDD(tupleRdd);
        // eliminate with static import
        JavaOpenSearchSpark.saveToOpenSearchWithMeta(pairRDD, target);

        assertEquals(2, JavaOpenSearchSpark.opensearchRDD(sc, target).count());
        assertTrue(RestUtils.exists(target + "/1"));
        assertTrue(RestUtils.exists(target + "/2"));
        String results = RestUtils.get(target + "/_search?");
        assertThat(results, containsString("SFO"));
    }

    // @Test
    public void testOpenSearchRDDWriteWithMappingExclude() throws Exception {
        Map<String, ?> doc1 = ImmutableMap.of("reason", "business", "airport", "SFO");
        Map<String, ?> doc2 = ImmutableMap.of("participants", 2, "airport", "OTP");

        String target = "spark-test-java-exclude-write/data";

        JavaRDD<Map<String, ?>> javaRDD = sc.parallelize(ImmutableList.of(doc1, doc2));
        JavaOpenSearchSpark.saveToOpenSearch(javaRDD, target, ImmutableMap.of(OPENSEARCH_MAPPING_EXCLUDE, "airport"));

        assertEquals(2, JavaOpenSearchSpark.opensearchRDD(sc, target).count());
        assertTrue(RestUtils.exists(target));
        assertThat(RestUtils.get(target + "/_search?"), containsString("business"));
        assertThat(RestUtils.get(target + "/_search?"), containsString("participants"));
        assertThat(RestUtils.get(target + "/_search?"), not(containsString("airport")));
    }

    // @Test
    public void testOpenSearchMultiIndexRDDWrite() throws Exception {
      Map<String, ?> doc1 = ImmutableMap.of("reason", "business", "airport", "SFO");
      Map<String, ?> doc2 = ImmutableMap.of("participants", 2, "airport", "OTP");

      String target = "spark-test-java-trip-{airport}/data";

      JavaRDD<Map<String, ?>> javaRDD = sc.parallelize(ImmutableList.of(doc1, doc2));
      JavaOpenSearchSpark.saveToOpenSearch(javaRDD, target);

      assertTrue(RestUtils.exists("spark-test-java-trip-OTP/data"));
      assertTrue(RestUtils.exists("spark-test-java-trip-SFO/data"));

      assertThat(RestUtils.get("spark-test-java-trip-SFO/data/_search?"), containsString("business"));
      assertThat(RestUtils.get("spark-test-java-trip-OTP/data/_search?"), containsString("participants"));
    }

    // @Test
    public void testOpenSearchRDDWriteAsJsonMultiWrite() throws Exception {
      String json1 = "{\"reason\" : \"business\",\"airport\" : \"SFO\"}";
      String json2 = "{\"participants\" : 5,\"airport\" : \"OTP\"}";

      JavaRDD<String> stringRDD = sc.parallelize(ImmutableList.of(json1, json2));
      JavaOpenSearchSpark.saveJsonToEs(stringRDD, "spark-test-json-{airport}/data");
      JavaOpenSearchSpark.saveJsonToEs(stringRDD, "spark-test-json1-{airport}/data", Collections.<String, String> emptyMap());
      JavaOpenSearchSpark.saveJsonToEs(stringRDD, ImmutableMap.of(OPENSEARCH_RESOURCE, "spark-test-json2-{airport}/data"));

      byte[] json1BA = json1.getBytes();
      byte[] json2BA = json2.getBytes();

      JavaRDD<byte[]> byteRDD = sc.parallelize(ImmutableList.of(json1BA, json2BA));
      JavaOpenSearchSpark.saveJsonByteArrayToEs(byteRDD, "spark-test-json-ba-{airport}/data");
      JavaOpenSearchSpark.saveJsonByteArrayToEs(byteRDD, "spark-test-json-ba1-{airport}/data", Collections.<String, String> emptyMap());
      JavaOpenSearchSpark.saveJsonByteArrayToEs(byteRDD, ImmutableMap.of(OPENSEARCH_RESOURCE, "spark-test-json-ba2-{airport}/data"));

      assertTrue(RestUtils.exists("spark-test-json-SFO/data"));
      assertTrue(RestUtils.exists("spark-test-json-OTP/data"));

      assertTrue(RestUtils.exists("spark-test-json1-SFO/data"));
      assertTrue(RestUtils.exists("spark-test-json1-OTP/data"));

      assertTrue(RestUtils.exists("spark-test-json2-SFO/data"));
      assertTrue(RestUtils.exists("spark-test-json2-OTP/data"));

      assertTrue(RestUtils.exists("spark-test-json-ba-SFO/data"));
      assertTrue(RestUtils.exists("spark-test-json-ba-OTP/data"));

      assertTrue(RestUtils.exists("spark-test-json-ba1-SFO/data"));
      assertTrue(RestUtils.exists("spark-test-json-ba1-OTP/data"));

      assertTrue(RestUtils.exists("spark-test-json-ba2-SFO/data"));
      assertTrue(RestUtils.exists("spark-test-json-ba2-OTP/data"));

      assertThat(RestUtils.get("spark-test-json-SFO/data/_search?"), containsString("business"));
      assertThat(RestUtils.get("spark-test-json-OTP/data/_search?"), containsString("participants"));
    }

    // @Test
    public void testOpenSearchRDDZRead() throws Exception {
        String target = "spark-test-java-basic-read/data";

        RestUtils.touch("spark-test-java-basic-read");
        RestUtils.postData(target, "{\"message\" : \"Hello World\",\"message_date\" : \"2014-05-25\"}".getBytes());
        RestUtils.postData(target, "{\"message\" : \"Goodbye World\",\"message_date\" : \"2014-05-25\"}".getBytes());
        RestUtils.refresh("spark-test*");

//        JavaRDD<scala.collection.Map<String, Object>> opensearchRDD = JavaOpenSearchSpark.opensearchRDD(sc, target);
//        JavaRDD messages = opensearchRDD.filter(new Function<scala.collection.Map<String, Object>, Boolean>() {
//            public Boolean call(scala.collection.Map<String, Object> map) {
//                for (Entry<String, Object> entry: JavaConversions.asJavaMap(map).entrySet()) {
//                    if (entry.getValue().toString().contains("message")) {
//                        return Boolean.TRUE;
//                    }
//                }
//                return Boolean.FALSE;
//            }
//        });

        JavaRDD<Map<String, Object>> opensearchRDD = JavaOpenSearchSpark.opensearchRDD(sc, target).values();
        System.out.println(opensearchRDD.collect());
        JavaRDD<Map<String, Object>> messages = opensearchRDD.filter(new Function<Map<String, Object>, Boolean>() {
            @Override
            public Boolean call(Map<String, Object> map) throws Exception {
                return map.containsKey("message");
            }
        });

        // jdk8
        //opensearchRDD.filter(m -> m.stream().filter(v -> v.contains("message")));

        assertThat((int) messages.count(), is(2));
        System.out.println(messages.take(10));
        System.out.println(messages);
    }


    // @Test
    public void testOpenSearchRDDZReadJson() throws Exception {
        String target = "spark-test-java-basic-json-read/data";

        RestUtils.touch("spark-test-java-basic-json-read");
        RestUtils.postData(target, "{\"message\" : \"Hello World\",\"message_date\" : \"2014-05-25\"}".getBytes());
        RestUtils.postData(target, "{\"message\" : \"Goodbye World\",\"message_date\" : \"2014-05-25\"}".getBytes());
        RestUtils.refresh("spark-test*");

        JavaRDD<String> opensearchRDD = JavaOpenSearchSpark.esJsonRDD(sc, target).values();
        System.out.println(opensearchRDD.collect());
        JavaRDD<String> messages = opensearchRDD.filter(new Function<String, Boolean>() {
            @Override
            public Boolean call(String string) throws Exception {
                return string.contains("message");
            }
        });

        // jdk8
        //opensearchRDD.filter(m -> m.contains("message")));

        assertThat((int) messages.count(), is(2));
        System.out.println(messages.take(10));
        System.out.println(messages);
    }

    @Test
    public void testOpenSearchRDDZReadWithGroupBy() throws Exception {
        String target = resource("spark-test-java-basic-group", "data", version);
        String docEndpoint = docEndpoint("spark-test-java-basic-group", "data", version);

        RestUtils.touch("spark-test-java-basic-group");
        RestUtils.postData(docEndpoint,
                "{\"message\" : \"Hello World\",\"message_date\" : \"2014-05-25\"}".getBytes());
        RestUtils.postData(docEndpoint,
                "{\"message\" : \"Goodbye World\",\"message_date\" : \"2014-05-25\"}".getBytes());
        RestUtils.refresh("spark-test-java-basic-group");

        assertThat(JavaOpenSearchSpark.esJsonRDD(sc, target).groupBy(pair -> pair._2).count(), is(2L));
    }

    // @Test(expected = OpenSearchHadoopIllegalArgumentException.class)
    public void testNoResourceSpecified() throws Exception {
        JavaRDD<Map<String, Object>> rdd = JavaOpenSearchSpark.opensearchRDD(sc).values();
        rdd.count();
    }

}