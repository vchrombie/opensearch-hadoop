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
package org.opensearch.hadoop.cfg;

import java.io.InputStream;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.commons.logging.LogFactory;
import org.opensearch.hadoop.OpenSearchHadoopIllegalArgumentException;
import org.opensearch.hadoop.security.AuthenticationMethod;
import org.opensearch.hadoop.util.ClusterName;
import org.opensearch.hadoop.util.OpenSearchMajorVersion;
import org.opensearch.hadoop.util.IOUtils;
import org.opensearch.hadoop.util.ClusterInfo;
import org.opensearch.hadoop.util.StringUtils;
import org.opensearch.hadoop.util.unit.Booleans;
import org.opensearch.hadoop.util.unit.ByteSizeValue;
import org.opensearch.hadoop.util.unit.TimeValue;

import static org.opensearch.hadoop.cfg.ConfigurationOptions.*;
import static org.opensearch.hadoop.cfg.ConfigurationOptions.OPENSEARCH_NET_HTTP_HEADER_OPAQUE_ID;
import static org.opensearch.hadoop.cfg.InternalConfigurationOptions.*;

/**
 * Holder class containing the various configuration bits used by ElasticSearch Hadoop. Handles internally the fall back to defaults when looking for undefined, optional settings.
 */
public abstract class Settings {
    /**
     * Get the internal version or throw an {@link IllegalArgumentException} if not present
     * @return The {@link OpenSearchMajorVersion} extracted from the properties
     */
    public OpenSearchMajorVersion getInternalVersionOrThrow() {
        String version = getProperty(InternalConfigurationOptions.INTERNAL_OPENSEARCH_VERSION, null);
        if (version == null) {
            throw new IllegalArgumentException("OpenSearch version:[ " + InternalConfigurationOptions.INTERNAL_OPENSEARCH_VERSION + "] not present in configuration");
        }
        return OpenSearchMajorVersion.parse(version);
    }

    /**
     * Get the internal version or {@link OpenSearchMajorVersion#LATEST} if not present
     * @return The {@link OpenSearchMajorVersion} extracted from the properties or {@link OpenSearchMajorVersion#LATEST} if not present
     * @deprecated This is kind of a dangerous method to use, because it assumes that you care about which version you are working with,
     *             but the version you receive from this call may not be accurate, and thus, cannot be trusted to let you make accurate
     *             decisions about the version of OpenSearch you are speaking with. Prefer to use the {@link Settings#getInternalVersionOrThrow()}
     *             instead.
     */
    @Deprecated
    public OpenSearchMajorVersion getInternalVersionOrLatest() {
        String version = getProperty(InternalConfigurationOptions.INTERNAL_OPENSEARCH_VERSION, null);
        if (version == null) {
            return OpenSearchMajorVersion.LATEST;
        }
        return OpenSearchMajorVersion.parse(version);
    }

    /**
     * Get the internal cluster name and version or throw an {@link IllegalArgumentException} if not present
     * @return the {@link ClusterInfo} extracted from the properties
     */
    public ClusterInfo getClusterInfoOrThrow() {
        ClusterInfo clusterInfo = getClusterInfoOrNull();
        if (clusterInfo == null) {
            throw new IllegalArgumentException("OpenSearch cluster name:[ " + InternalConfigurationOptions.INTERNAL_OPENSEARCH_CLUSTER_NAME +
                    "] not present in configuration");
        }
        return clusterInfo;
    }

    /**
     * Get the internal cluster name and version or null if not present in the settings
     * @return the {@link ClusterInfo} extracted from the properties or null if not present
     */
    public ClusterInfo getClusterInfoOrNull() {
        String clusterName = getProperty(InternalConfigurationOptions.INTERNAL_OPENSEARCH_CLUSTER_NAME);
        if (clusterName == null) {
            return null;
        }
        String clusterUUID = getProperty(InternalConfigurationOptions.INTERNAL_OPENSEARCH_CLUSTER_UUID);
        OpenSearchMajorVersion version = getInternalVersionOrThrow();
        return new ClusterInfo(new ClusterName(clusterName, clusterUUID), version);
    }

    /**
     * Get the internal cluster name and version or throw an {@link IllegalArgumentException} if not present
     * @return the {@link ClusterInfo} extracted from the properties
     * @deprecated This is a dangerous method to use, because it assumes that you care about which cluster you are working with,
     *     but the info you receive from this call may not be accurate, and thus, cannot be trusted to let you make accurate
     *     decisions about the OpenSearch cluster you are speaking with. Prefer to use the {@link Settings#getClusterInfoOrThrow()}
     *     instead.
     */
    @Deprecated
    public ClusterInfo getClusterInfoOrUnnamedLatest() {
        String clusterName = getProperty(InternalConfigurationOptions.INTERNAL_OPENSEARCH_CLUSTER_NAME);
        if (clusterName == null) {
            return ClusterInfo.unnamedLatest();
        }
        String clusterUUID = getProperty(InternalConfigurationOptions.INTERNAL_OPENSEARCH_CLUSTER_UUID);
        OpenSearchMajorVersion version = getInternalVersionOrLatest();
        return new ClusterInfo(new ClusterName(clusterName, clusterUUID), version);
    }

    public String getNodes() {
        return getProperty(OPENSEARCH_NODES, OPENSEARCH_NODES_DEFAULT);
    }

    public int getPort() {
        return Integer.valueOf(getProperty(OPENSEARCH_PORT, OPENSEARCH_PORT_DEFAULT));
    }

    public boolean getNodesDiscovery() {
        // by default, if not set, return a value compatible with the WAN setting
        // otherwise return the user value.
        // this helps validate the configuration
        return Booleans.parseBoolean(getProperty(OPENSEARCH_NODES_DISCOVERY), !getNodesWANOnly());
    }

    public String getShardPreference() { return getProperty(OPENSEARCH_READ_SHARD_PREFERENCE, OPENSEARCH_READ_SHARD_PREFERENCE_DEFAULT); }

    public String getNodesPathPrefix() {
        return getProperty(OPENSEARCH_NODES_PATH_PREFIX, OPENSEARCH_NODES_PATH_PREFIX_DEFAULT);
    }

    public boolean getNodesDataOnly() {
        // by default, if not set, return a value compatible with the other settings
        return Booleans.parseBoolean(getProperty(OPENSEARCH_NODES_DATA_ONLY), !getNodesWANOnly() && !getNodesClientOnly() && !getNodesIngestOnly());
    }

    public boolean getNodesIngestOnly() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_NODES_INGEST_ONLY, OPENSEARCH_NODES_INGEST_ONLY_DEFAULT));
    }

    public boolean getNodesClientOnly() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_NODES_CLIENT_ONLY, OPENSEARCH_NODES_CLIENT_ONLY_DEFAULT));
    }

    public boolean getNodesWANOnly() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_NODES_WAN_ONLY, OPENSEARCH_NODES_WAN_ONLY_DEFAULT));
    }

    public long getHttpTimeout() {
        return TimeValue.parseTimeValue(getProperty(OPENSEARCH_HTTP_TIMEOUT, OPENSEARCH_HTTP_TIMEOUT_DEFAULT)).getMillis();
    }

    public int getHttpRetries() {
        return Integer.valueOf(getProperty(OPENSEARCH_HTTP_RETRIES, OPENSEARCH_HTTP_RETRIES_DEFAULT));
    }

    public int getBatchSizeInBytes() {
        return ByteSizeValue.parseBytesSizeValue(getProperty(OPENSEARCH_BATCH_SIZE_BYTES, OPENSEARCH_BATCH_SIZE_BYTES_DEFAULT)).bytesAsInt();
    }

    public int getBatchSizeInEntries() {
        return Integer.valueOf(getProperty(OPENSEARCH_BATCH_SIZE_ENTRIES, OPENSEARCH_BATCH_SIZE_ENTRIES_DEFAULT));
    }

    public int getBatchWriteRetryCount() {
        return Integer.parseInt(getProperty(OPENSEARCH_BATCH_WRITE_RETRY_COUNT, OPENSEARCH_BATCH_WRITE_RETRY_COUNT_DEFAULT));
    }

    public int getBatchWriteRetryLimit() {
        return Integer.parseInt(getProperty(OPENSEARCH_BATCH_WRITE_RETRY_LIMIT, OPENSEARCH_BATCH_WRITE_RETRY_LIMIT_DEFAULT));
    }

    public long getBatchWriteRetryWait() {
        return TimeValue.parseTimeValue(getProperty(OPENSEARCH_BATCH_WRITE_RETRY_WAIT, OPENSEARCH_BATCH_WRITE_RETRY_WAIT_DEFAULT)).getMillis();
    }

    public String getBatchWriteRetryPolicy() {
        return getProperty(OPENSEARCH_BATCH_WRITE_RETRY_POLICY, OPENSEARCH_BATCH_WRITE_RETRY_POLICY_DEFAULT);
    }

    public boolean getBatchRefreshAfterWrite() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_BATCH_WRITE_REFRESH, OPENSEARCH_BATCH_WRITE_REFRESH_DEFAULT));
    }

    public boolean getBatchFlushManual() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_BATCH_FLUSH_MANUAL, OPENSEARCH_BATCH_FLUSH_MANUAL_DEFAULT));
    }

    public long getScrollKeepAlive() {
        return TimeValue.parseTimeValue(getProperty(OPENSEARCH_SCROLL_KEEPALIVE, OPENSEARCH_SCROLL_KEEPALIVE_DEFAULT)).getMillis();
    }

    public long getScrollSize() {
        return Long.valueOf(getProperty(OPENSEARCH_SCROLL_SIZE, OPENSEARCH_SCROLL_SIZE_DEFAULT));
    }

    public long getScrollLimit() {
        return Long.valueOf(getProperty(OPENSEARCH_SCROLL_LIMIT, OPENSEARCH_SCROLL_LIMIT_DEFAULT));
    }

    public String getScrollFields() {
        return getProperty(INTERNAL_OPENSEARCH_TARGET_FIELDS);
    }

    public boolean getExcludeSource() {
        return Booleans.parseBoolean(getProperty(INTERNAL_OPENSEARCH_EXCLUDE_SOURCE, INTERNAL_OPENSEARCH_EXCLUDE_SOURCE_DEFAULT));
    }

    public String getSerializerValueWriterClassName() {
        return getProperty(OPENSEARCH_SERIALIZATION_WRITER_VALUE_CLASS);
    }


    public String getSerializerBytesConverterClassName() {
        return getProperty(OPENSEARCH_SERIALIZATION_WRITER_BYTES_CLASS);
    }

    public String getSerializerValueReaderClassName() {
        return getProperty(OPENSEARCH_SERIALIZATION_READER_VALUE_CLASS);
    }

    public boolean getIndexAutoCreate() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_INDEX_AUTO_CREATE, OPENSEARCH_INDEX_AUTO_CREATE_DEFAULT));
    }

    public boolean getIndexReadMissingAsEmpty() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_INDEX_READ_MISSING_AS_EMPTY, OPENSEARCH_INDEX_READ_MISSING_AS_EMPTY_DEFAULT));
    }

    public boolean getIndexReadAllowRedStatus() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_INDEX_READ_ALLOW_RED_STATUS, OPENSEARCH_INDEX_READ_ALLOW_RED_STATUS_DEFAULT));
    }

    public boolean getInputAsJson() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_INPUT_JSON, OPENSEARCH_INPUT_JSON_DEFAULT));
    }

    public boolean getOutputAsJson() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_OUTPUT_JSON, OPENSEARCH_OUTPUT_JSON_DEFAULT));
    }

    public String getOperation() {
        return getProperty(OPENSEARCH_WRITE_OPERATION, OPENSEARCH_WRITE_OPERATION_DEFAULT).toLowerCase(Locale.ROOT);
    }

    public String getMappingId() {
        return getProperty(OPENSEARCH_MAPPING_ID);
    }

    public String getMappingParent() {
        return getProperty(OPENSEARCH_MAPPING_PARENT);
    }

    public String getMappingJoin() {
        return getProperty(OPENSEARCH_MAPPING_JOIN);
    }

    public String getMappingVersion() {
        return getProperty(OPENSEARCH_MAPPING_VERSION);
    }

    public boolean hasMappingVersionType() {
        String versionType = getMappingVersionType();
        return (StringUtils.hasText(getMappingVersion()) && StringUtils.hasText(versionType) && !versionType.equals(OPENSEARCH_MAPPING_VERSION_TYPE_INTERNAL));
    }

    public String getMappingVersionType() {
        return getProperty(OPENSEARCH_MAPPING_VERSION_TYPE, OPENSEARCH_MAPPING_VERSION_TYPE_EXTERNAL);
    }

    public String getMappingRouting() {
        return getProperty(OPENSEARCH_MAPPING_ROUTING);
    }

    public String getMappingTtl() {
        return getProperty(OPENSEARCH_MAPPING_TTL);
    }

    public String getMappingTimestamp() {
        return getProperty(OPENSEARCH_MAPPING_TIMESTAMP);
    }

    public String getMappingDefaultClassExtractor() {
        return getProperty(OPENSEARCH_MAPPING_DEFAULT_EXTRACTOR_CLASS);
    }
    
    public String getMappingMetadataExtractorClassName() {
        return getProperty(OPENSEARCH_MAPPING_METADATA_EXTRACTOR_CLASS);
    }

    public String getMappingIdExtractorClassName() {
        return getProperty(OPENSEARCH_MAPPING_ID_EXTRACTOR_CLASS, getMappingDefaultClassExtractor());
    }

    public String getMappingParentExtractorClassName() {
        return getProperty(OPENSEARCH_MAPPING_PARENT_EXTRACTOR_CLASS, getMappingDefaultClassExtractor());
    }

    public String getMappingJoinExtractorClassName() {
        return getProperty(OPENSEARCH_MAPPING_JOIN_EXTRACTOR_CLASS, getMappingDefaultClassExtractor());
    }

    public String getMappingVersionExtractorClassName() {
        return getProperty(OPENSEARCH_MAPPING_VERSION_EXTRACTOR_CLASS, getMappingDefaultClassExtractor());
    }

    public String getMappingRoutingExtractorClassName() {
        return getProperty(OPENSEARCH_MAPPING_ROUTING_EXTRACTOR_CLASS, getMappingDefaultClassExtractor());
    }

    public String getMappingTtlExtractorClassName() {
        return getProperty(OPENSEARCH_MAPPING_TTL_EXTRACTOR_CLASS, getMappingDefaultClassExtractor());
    }

    public String getMappingTimestampExtractorClassName() {
        return getProperty(OPENSEARCH_MAPPING_TIMESTAMP_EXTRACTOR_CLASS, getMappingDefaultClassExtractor());
    }

    public String getMappingIndexExtractorClassName() {
        return getProperty(OPENSEARCH_MAPPING_INDEX_EXTRACTOR_CLASS, OPENSEARCH_MAPPING_DEFAULT_INDEX_EXTRACTOR_CLASS);
    }

    public String getMappingIndexFormatterClassName() {
        return getProperty(OPENSEARCH_MAPPING_INDEX_FORMATTER_CLASS, OPENSEARCH_MAPPING_DEFAULT_INDEX_FORMATTER_CLASS);
    }

    public String getMappingParamsExtractorClassName() {
        return getProperty(OPENSEARCH_MAPPING_PARAMS_EXTRACTOR_CLASS, OPENSEARCH_MAPPING_PARAMS_DEFAULT_EXTRACTOR_CLASS);
    }

    public boolean getMappingConstantAutoQuote() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_MAPPING_CONSTANT_AUTO_QUOTE, OPENSEARCH_MAPPING_CONSTANT_AUTO_QUOTE_DEFAULT));
    }

    public boolean getMappingDateRich() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_MAPPING_DATE_RICH_OBJECT, OPENSEARCH_MAPPING_DATE_RICH_OBJECT_DEFAULT));
    }

    public String getMappingIncludes() {
        return getProperty(OPENSEARCH_MAPPING_INCLUDE, OPENSEARCH_MAPPING_INCLUDE_DEFAULT);
    }

    public String getMappingExcludes() {
        return getProperty(OPENSEARCH_MAPPING_EXCLUDE, OPENSEARCH_MAPPING_EXCLUDE_DEFAULT);
    }

    public String getIngestPipeline() { return getProperty(OPENSEARCH_INGEST_PIPELINE, OPENSEARCH_INGEST_PIPELINE_DEFAULT); }

    public int getUpdateRetryOnConflict() {
        return Integer.parseInt(getProperty(OPENSEARCH_UPDATE_RETRY_ON_CONFLICT, OPENSEARCH_UPDATE_RETRY_ON_CONFLICT_DEFAULT));
    }

    public String getUpdateScript() {
        return getProperty(OPENSEARCH_UPDATE_SCRIPT_LEGACY);
    }

    public String getUpdateScriptInline() {
        return getLegacyProperty(OPENSEARCH_UPDATE_SCRIPT_LEGACY, OPENSEARCH_UPDATE_SCRIPT_INLINE, null);
    }

    public Boolean getUpdateScriptUpsert() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_UPDATE_SCRIPT_UPSERT, OPENSEARCH_UPDATE_SCRIPT_UPSERT_DEFAULT));
    }

    public String getUpdateScriptFile() {
        return getProperty(OPENSEARCH_UPDATE_SCRIPT_FILE);
    }

    public String getUpdateScriptStored() {
        return getProperty(OPENSEARCH_UPDATE_SCRIPT_STORED);
    }

    public String getUpdateScriptLang() {
        return getProperty(OPENSEARCH_UPDATE_SCRIPT_LANG);
    }

    public String getUpdateScriptParams() {
        return getProperty(OPENSEARCH_UPDATE_SCRIPT_PARAMS);
    }

    public String getUpdateScriptParamsJson() {
        return getProperty(OPENSEARCH_UPDATE_SCRIPT_PARAMS_JSON);
    }

    public boolean hasUpdateScript() {
        String op = getOperation();
        boolean hasScript = false;
        if (ConfigurationOptions.OPENSEARCH_OPERATION_UPDATE.equals(op) || ConfigurationOptions.OPENSEARCH_OPERATION_UPSERT.equals(op)) {
            hasScript = StringUtils.hasText(getUpdateScriptInline());
            hasScript |= StringUtils.hasText(getUpdateScriptFile());
            hasScript |= StringUtils.hasText(getUpdateScriptStored());
        }
        return hasScript;
    }

    public boolean hasUpdateScriptParams() {
        return hasUpdateScript() && StringUtils.hasText(getUpdateScriptParams());
    }

    public boolean hasUpdateScriptParamsJson() {
        return hasUpdateScript() && StringUtils.hasText(getUpdateScriptParamsJson());
    }

    public boolean hasScriptUpsert() {
        String op = getOperation();
        return ConfigurationOptions.OPENSEARCH_OPERATION_UPSERT.equals(op) && getUpdateScriptUpsert();
    }

    private String getLegacyProperty(String legacyProperty, String newProperty, String defaultValue) {
        String legacy = getProperty(legacyProperty);
        if (StringUtils.hasText(legacy)) {
            LogFactory.getLog(Settings.class).warn(String.format(Locale.ROOT, "[%s] property has been deprecated - use [%s] instead", legacyProperty, newProperty));
            return legacy;
        }
        return getProperty(newProperty, defaultValue);
    }

    public boolean getReadFieldEmptyAsNull() {
        return Booleans.parseBoolean(getLegacyProperty(OPENSEARCH_READ_FIELD_EMPTY_AS_NULL_LEGACY, OPENSEARCH_READ_FIELD_EMPTY_AS_NULL, OPENSEARCH_READ_FIELD_EMPTY_AS_NULL_DEFAULT));
    }

    public FieldPresenceValidation getReadFieldExistanceValidation() {
        return FieldPresenceValidation.valueOf(getLegacyProperty(OPENSEARCH_READ_FIELD_VALIDATE_PRESENCE_LEGACY, OPENSEARCH_READ_FIELD_VALIDATE_PRESENCE, OPENSEARCH_READ_FIELD_VALIDATE_PRESENCE_DEFAULT).toUpperCase(Locale.ENGLISH));
    }

    public String getReadFieldInclude() {
        return getProperty(OPENSEARCH_READ_FIELD_INCLUDE, StringUtils.EMPTY);
    }

    public String getReadFieldExclude() {
        return getProperty(OPENSEARCH_READ_FIELD_EXCLUDE, StringUtils.EMPTY);
    }

    public String getReadFieldAsArrayInclude() {
        return getProperty(OPENSEARCH_READ_FIELD_AS_ARRAY_INCLUDE, StringUtils.EMPTY);
    }

    public String getReadFieldAsArrayExclude() {
        return getProperty(OPENSEARCH_READ_FIELD_AS_ARRAY_EXCLUDE, StringUtils.EMPTY);
    }

    public String getReadSourceFilter() {
        return getProperty(OPENSEARCH_READ_SOURCE_FILTER, StringUtils.EMPTY);
    }

    public TimeValue getHeartBeatLead() {
        return TimeValue.parseTimeValue(getProperty(OPENSEARCH_HEART_BEAT_LEAD, OPENSEARCH_HEART_BEAT_LEAD_DEFAULT));
    }

    public TimeValue getTransportPoolingExpirationTimeout() {
        return TimeValue.parseTimeValue(getProperty(OPENSEARCH_NET_TRANSPORT_POOLING_EXPIRATION_TIMEOUT, OPENSEARCH_NET_TRANSPORT_POOLING_EXPIRATION_TIMEOUT_DEFAULT));
    }

    // SSL
    public boolean getNetworkSSLEnabled() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_NET_USE_SSL, OPENSEARCH_NET_USE_SSL_DEFAULT));
    }

    public String getNetworkSSLKeyStoreLocation() {
        return getProperty(OPENSEARCH_NET_SSL_KEYSTORE_LOCATION);
    }

    public String getNetworkSSLProtocol() {
        return getProperty(OPENSEARCH_NET_SSL_PROTOCOL, OPENSEARCH_NET_SSL_PROTOCOL_DEFAULT);
    }

    public String getNetworkSSLKeyStoreType() {
        return getProperty(OPENSEARCH_NET_SSL_KEYSTORE_TYPE, OPENSEARCH_NET_SSL_KEYSTORE_TYPE_DEFAULT);
    }

    @Deprecated
    public String getNetworkSSLKeyStorePass() {
        return getProperty(OPENSEARCH_NET_SSL_KEYSTORE_PASS);
    }

    public String getNetworkSSLTrustStoreLocation() {
        return getProperty(OPENSEARCH_NET_SSL_TRUST_STORE_LOCATION);
    }

    @Deprecated
    public String getNetworkSSLTrustStorePass() {
        return getProperty(OPENSEARCH_NET_SSL_TRUST_STORE_PASS);
    }

    public boolean getNetworkSSLAcceptSelfSignedCert() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_NET_SSL_CERT_ALLOW_SELF_SIGNED, OPENSEARCH_NET_SSL_CERT_ALLOW_SELF_SIGNED_DEFAULT));
    }

    public String getNetworkHttpAuthUser() {
        return getProperty(OPENSEARCH_NET_HTTP_AUTH_USER);
    }

    @Deprecated
    public String getNetworkHttpAuthPass() {
        return getProperty(OPENSEARCH_NET_HTTP_AUTH_PASS);
    }

    public String getNetworkSpnegoAuthElasticsearchPrincipal() {
        return getProperty(OPENSEARCH_NET_SPNEGO_AUTH_OPENSEARCH_PRINCIPAL);
    }

    public boolean getNetworkSpnegoAuthMutual() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_NET_SPNEGO_AUTH_MUTUAL, OPENSEARCH_NET_SPNEGO_AUTH_MUTUAL_DEFAULT));
    }

    public String getNetworkProxyHttpHost() {
        return getProperty(OPENSEARCH_NET_PROXY_HTTP_HOST);
    }

    public int getNetworkProxyHttpPort() {
        return Integer.valueOf(getProperty(OPENSEARCH_NET_PROXY_HTTP_PORT, "-1"));
    }

    public String getNetworkProxyHttpUser() {
        return getProperty(OPENSEARCH_NET_PROXY_HTTP_USER);
    }

    @Deprecated
    public String getNetworkProxyHttpPass() {
        return getProperty(OPENSEARCH_NET_PROXY_HTTP_PASS);
    }

    public boolean getNetworkHttpUseSystemProperties() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_NET_PROXY_HTTP_USE_SYSTEM_PROPS, OPENSEARCH_NET_PROXY_HTTP_USE_SYSTEM_PROPS_DEFAULT));
    }

    public String getNetworkProxyHttpsHost() {
        return getProperty(OPENSEARCH_NET_PROXY_HTTPS_HOST);
    }

    public int getNetworkProxyHttpsPort() {
        return Integer.valueOf(getProperty(OPENSEARCH_NET_PROXY_HTTPS_PORT, "-1"));
    }

    public String getNetworkProxyHttpsUser() {
        return getProperty(OPENSEARCH_NET_PROXY_HTTPS_USER);
    }

    @Deprecated
    public String getNetworkProxyHttpsPass() {
        return getProperty(OPENSEARCH_NET_PROXY_HTTPS_PASS);
    }

    public boolean getNetworkHttpsUseSystemProperties() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_NET_PROXY_HTTPS_USE_SYSTEM_PROPS, OPENSEARCH_NET_PROXY_HTTPS_USE_SYSTEM_PROPS_DEFAULT));
    }

    public String getNetworkProxySocksHost() {
        return getProperty(OPENSEARCH_NET_PROXY_SOCKS_HOST);
    }

    public int getNetworkProxySocksPort() {
        return Integer.valueOf(getProperty(OPENSEARCH_NET_PROXY_SOCKS_PORT, "-1"));
    }

    public String getNetworkProxySocksUser() {
        return getProperty(OPENSEARCH_NET_PROXY_SOCKS_USER);
    }

    @Deprecated
    public String getNetworkProxySocksPass() {
        return getProperty(OPENSEARCH_NET_PROXY_SOCKS_PASS);
    }

    public boolean getNetworkSocksUseSystemProperties() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_NET_PROXY_SOCKS_USE_SYSTEM_PROPS, OPENSEARCH_NET_PROXY_SOCKS_USE_SYSTEM_PROPS_DEFAULT));
    }

    public boolean getNodesResolveHostnames() {
        // by default, if not set, return a value compatible with the WAN setting
        // otherwise return the user value.
        // this helps validate the configuration
        return Booleans.parseBoolean(getProperty(OPENSEARCH_NODES_RESOLVE_HOST_NAME), !getNodesWANOnly());
    }

    public Settings setInternalClusterInfo(ClusterInfo clusterInfo) {
        setProperty(INTERNAL_OPENSEARCH_CLUSTER_NAME, clusterInfo.getClusterName().getName());
        if (clusterInfo.getClusterName().getUUID() != null) {
            setProperty(INTERNAL_OPENSEARCH_CLUSTER_UUID, clusterInfo.getClusterName().getUUID());
        }
        setProperty(INTERNAL_OPENSEARCH_VERSION, clusterInfo.getMajorVersion().toString());
        return this;
    }

    /**
     * @deprecated prefer to use Settings#setInternalClusterInfo
     */
    @Deprecated
    public Settings setInternalVersion(OpenSearchMajorVersion version) {
        setProperty(INTERNAL_OPENSEARCH_VERSION, version.toString());
        return this;
    }

    public Settings setNodes(String hosts) {
        setProperty(OPENSEARCH_NODES, hosts);
        return this;
    }

    @Deprecated
    public Settings setHosts(String hosts) {
        return setNodes(hosts);
    }

    public Settings setPort(int port) {
        setProperty(OPENSEARCH_PORT, "" + port);
        return this;
    }

    public Settings setResourceRead(String index) {
        setProperty(OPENSEARCH_RESOURCE_READ, index);
        return this;
    }

    public Settings setResourceWrite(String index) {
        setProperty(OPENSEARCH_RESOURCE_WRITE, index);
        return this;
    }

    public Settings setQuery(String query) {
        setProperty(OPENSEARCH_QUERY, StringUtils.hasText(query) ? query : "");
        return this;
    }

    public Settings setMaxDocsPerPartition(int size) {
        setProperty(OPENSEARCH_MAX_DOCS_PER_PARTITION, Integer.toString(size));
        return this;
    }

    protected String getResource() {
        return getProperty(OPENSEARCH_RESOURCE);
    }

    public String getResourceRead() {
        return getProperty(OPENSEARCH_RESOURCE_READ, getResource());
    }

    public String getResourceWrite() {
        return getProperty(OPENSEARCH_RESOURCE_WRITE, getResource());
    }

    public String getQuery() {
        return getProperty(OPENSEARCH_QUERY);
    }

    public Integer getMaxDocsPerPartition() {
        String value = getProperty(OPENSEARCH_MAX_DOCS_PER_PARTITION);
        if (StringUtils.hasText(value)) {
            return Integer.parseInt(value);
        }
        return null;
    }

    public boolean getReadMetadata() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_READ_METADATA, OPENSEARCH_READ_METADATA_DEFAULT));
    }

    public String getReadMetadataField() {
        return getProperty(OPENSEARCH_READ_METADATA_FIELD, OPENSEARCH_READ_METADATA_FIELD_DEFAULT);
    }

    public boolean getReadMetadataVersion() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_READ_METADATA_VERSION, OPENSEARCH_READ_METADATA_VERSION_DEFAULT));
    }

    public boolean getReadMappingMissingFieldsIgnore() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_READ_UNMAPPED_FIELDS_IGNORE, OPENSEARCH_READ_UNMAPPED_FIELDS_IGNORE_DEFAULT));
    }

    public boolean getDataFrameWriteNullValues() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_SPARK_DATAFRAME_WRITE_NULL_VALUES, OPENSEARCH_SPARK_DATAFRAME_WRITE_NULL_VALUES_DEFAULT));
    }

    public AuthenticationMethod getSecurityAuthenticationMethod() {
        AuthenticationMethod authMode = null;
        String authSetting = getProperty(ConfigurationOptions.OPENSEARCH_SECURITY_AUTHENTICATION);
        // Check for a valid auth setting
        if (authSetting != null) {
            authMode = AuthenticationMethod.get(authSetting);
            if (authMode == null) {
                // Property was set but was invalid auth mode value
                throw new OpenSearchHadoopIllegalArgumentException("Could not determine auth mode. Property [" +
                        ConfigurationOptions.OPENSEARCH_SECURITY_AUTHENTICATION + "] was set to unknown mode [" + authSetting + "]. " +
                        "Use a valid auth mode from the following: " + AuthenticationMethod.getAvailableMethods());
            }
        }
        // Check if user name is set in the settings for backwards compatibility.
        if (authMode == null && getNetworkHttpAuthUser() != null) {
            authMode = AuthenticationMethod.BASIC;
        } else if (authMode == null) {
            authMode = AuthenticationMethod.SIMPLE;
        }
        return authMode;
    }

    public String getSecurityUserProviderClass() {
        return getProperty(ConfigurationOptions.OPENSEARCH_SECURITY_USER_PROVIDER_CLASS);
    }

    public abstract InputStream loadResource(String location);

    public abstract Settings copy();

    public String getProperty(String name, String defaultValue) {
        String value = getProperty(name);
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return value;
    }

    public abstract String getProperty(String name);

    public abstract void setProperty(String name, String value);

    public Settings getSettingsView(String name) {
        return new SettingsView(this, name);
    }

    public Settings excludeFilter(String prefix) {
        return new FilteredSettings(this, prefix);
    }

    public Settings merge(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return this;
        }

        Enumeration<?> propertyNames = properties.propertyNames();

        Object prop = null;
        for (; propertyNames.hasMoreElements();) {
            prop = propertyNames.nextElement();
            if (prop instanceof String) {
                Object value = properties.get(prop);
                setProperty((String) prop, value.toString());
            }
        }

        return this;
    }

    public Settings merge(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return this;
        }

        for (Entry<String, String> entry : map.entrySet()) {
            setProperty(entry.getKey(), entry.getValue());
        }

        return this;
    }

    public Settings load(String source) {
        Properties copy = IOUtils.propsFromString(source);
        merge(copy);
        return this;
    }

    public String save() {
        Properties copy = asProperties();
        return IOUtils.propsToString(copy);
    }

    public abstract Properties asProperties();

    public Settings setOpaqueId(String opaqueId) {
        setProperty(OPENSEARCH_NET_HTTP_HEADER_OPAQUE_ID, cleanOpaqueId(opaqueId));
        return this;
    }

    /**
     * Headers can't contain newlines or non-ascii characters. This method strips them out, returning whatever is left.
     * @param opaqueId
     * @return
     */
    private String cleanOpaqueId(String opaqueId) {
        char[] chars = opaqueId.toCharArray();
        StringBuilder cleanedOpaqueId = new StringBuilder(chars.length);
        for (int i = 0; i < chars.length; i++) {
            int character = chars[i];
            if (character > 31 && character < 127) { //visible ascii
                cleanedOpaqueId.append(chars[i]);
            }
        }
        return cleanedOpaqueId.toString();
    }

    public String getOpaqueId() {
        return getProperty(OPENSEARCH_NET_HTTP_HEADER_OPAQUE_ID);
    }

    public Settings setUserAgent(String userAgent) {
        setProperty(OPENSEARCH_NET_HTTP_HEADER_USER_AGENT, cleanOpaqueId(userAgent));
        return this;
    }

    public Boolean getAwsSigV4Enabled() {
        return Booleans.parseBoolean(getProperty(OPENSEARCH_AWS_SIGV4_ENABLED, OPENSEARCH_AWS_SIGV4_ENABLED_DEFAULT));
    }

    public String getAwsSigV4Region() {
        return getProperty(OPENSEARCH_AWS_SIGV4_REGION, OPENSEARCH_AWS_SIGV4_REGION_DEFAULT);
    }

    public String getAwsSigV4ServiceName() {
        return getProperty(OPENSEARCH_AWS_SIGV4_SERVICE_NAME, OPENSEARCH_AWS_SIGV4_SERVICE_NAME_DEFAULT);
    }
}