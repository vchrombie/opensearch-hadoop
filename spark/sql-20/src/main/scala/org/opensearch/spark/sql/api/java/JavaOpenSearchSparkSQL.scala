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
package org.opensearch.spark.sql.api.java

import java.util.{ Map => JMap }

import scala.collection.JavaConverters.mapAsScalaMapConverter
import scala.collection.{ Map => SMap }

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.SparkSession
import org.opensearch.hadoop.cfg.ConfigurationOptions.OPENSEARCH_QUERY
import org.opensearch.hadoop.cfg.ConfigurationOptions.OPENSEARCH_RESOURCE_READ
import org.opensearch.spark.sql.OpenSearchSparkSQL

object JavaOpenSearchSparkSQL {

  // specify the return types to make sure the bytecode is generated properly (w/o any scala.collections in it)
  def esDF(sc: SQLContext): DataFrame = OpenSearchSparkSQL.esDF(sc, SMap.empty[String, String])
  def esDF(sc: SQLContext, resource: String): DataFrame = OpenSearchSparkSQL.esDF(sc, Map(OPENSEARCH_RESOURCE_READ -> resource))
  def esDF(sc: SQLContext, resource: String, query: String): DataFrame = OpenSearchSparkSQL.esDF(sc, Map(OPENSEARCH_RESOURCE_READ -> resource, OPENSEARCH_QUERY -> query))
  def esDF(sc: SQLContext, cfg: JMap[String, String]): DataFrame = OpenSearchSparkSQL.esDF(sc, cfg.asScala)
  def esDF(sc: SQLContext, resource: String, cfg: JMap[String, String]): DataFrame = OpenSearchSparkSQL.esDF(sc, resource, cfg.asScala)
  def esDF(sc: SQLContext, resource: String, query: String, cfg: JMap[String, String]): DataFrame = OpenSearchSparkSQL.esDF(sc, resource, query, cfg.asScala)

  def esDF(ss: SparkSession): DataFrame = OpenSearchSparkSQL.esDF(ss, SMap.empty[String, String])
  def esDF(ss: SparkSession, resource: String): DataFrame = OpenSearchSparkSQL.esDF(ss, Map(OPENSEARCH_RESOURCE_READ -> resource))
  def esDF(ss: SparkSession, resource: String, query: String): DataFrame = OpenSearchSparkSQL.esDF(ss, Map(OPENSEARCH_RESOURCE_READ -> resource, OPENSEARCH_QUERY -> query))
  def esDF(ss: SparkSession, cfg: JMap[String, String]): DataFrame = OpenSearchSparkSQL.esDF(ss, cfg.asScala)
  def esDF(ss: SparkSession, resource: String, cfg: JMap[String, String]): DataFrame = OpenSearchSparkSQL.esDF(ss, resource, cfg.asScala)
  def esDF(ss: SparkSession, resource: String, query: String, cfg: JMap[String, String]): DataFrame = OpenSearchSparkSQL.esDF(ss, resource, query, cfg.asScala)

  def saveToOpenSearch[T](ds: Dataset[T], resource: String): Unit = OpenSearchSparkSQL.saveToOpenSearch(ds , resource)
  def saveToOpenSearch[T](ds: Dataset[T], resource: String, cfg: JMap[String, String]): Unit = OpenSearchSparkSQL.saveToOpenSearch(ds, resource, cfg.asScala)
  def saveToOpenSearch[T](ds: Dataset[T], cfg: JMap[String, String]): Unit = OpenSearchSparkSQL.saveToOpenSearch(ds, cfg.asScala)
}