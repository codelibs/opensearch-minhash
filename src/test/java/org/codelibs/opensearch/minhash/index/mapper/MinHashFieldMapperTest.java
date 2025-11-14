/*
 * Copyright 2012-2025 CodeLibs Project and the Others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.codelibs.opensearch.minhash.index.mapper;

import static org.codelibs.opensearch.runner.OpenSearchRunner.newConfigs;

import java.util.Base64;
import java.util.Map;

import org.codelibs.opensearch.runner.OpenSearchRunner;
import org.opensearch.action.DocWriteResponse.Result;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.common.document.DocumentField;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.Settings.Builder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;

import com.google.common.collect.Lists;

import junit.framework.TestCase;

public class MinHashFieldMapperTest extends TestCase {

    private OpenSearchRunner runner;
    private String clusterName;

    @Override
    protected void setUp() throws Exception {
        clusterName = "es-minhash-mapper-test-" + System.currentTimeMillis();
        runner = new OpenSearchRunner();
        runner.onBuild(new OpenSearchRunner.Builder() {
            @Override
            public void build(final int number, final Builder settingsBuilder) {
                settingsBuilder.put("http.cors.enabled", true);
                settingsBuilder.put("http.cors.allow-origin", "*");
                settingsBuilder.put("discovery.type", "single-node");
            }
        }).build(newConfigs().clusterName(clusterName).numOfNode(1)
                .pluginTypes("org.codelibs.opensearch.minhash.MinHashPlugin"));
        runner.ensureYellow();
    }

    @Override
    protected void tearDown() throws Exception {
        runner.close();
        runner.clean();
    }

    public void test_basicMinHashField() throws Exception {
        final String index = "test_basic";

        // Create index with minhash analyzer
        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"minhash_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, XContentType.JSON).build());
        runner.ensureYellow(index);

        // Create mapping with minhash field
        final XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("content")
                .field("type", "text")
                .field("copy_to", "minhash_value")
                .endObject()
                .startObject("minhash_value")
                .field("type", "minhash")
                .field("minhash_analyzer", "minhash_analyzer")
                .endObject()
                .endObject()
                .endObject();

        runner.createMapping(index, mappingBuilder);

        // Index a document
        final IndexResponse indexResponse = runner.insert(index, "1",
                "{\"content\":\"This is a test document\"}");
        assertEquals(Result.CREATED, indexResponse.getResult());
        runner.refresh();

        // Retrieve and verify
        final GetResponse response = runner.client().prepareGet(index, "1")
                .setStoredFields("minhash_value")
                .execute()
                .actionGet();

        assertTrue(response.isExists());
        final DocumentField field = response.getField("minhash_value");
        assertNotNull(field);
        final String value = (String) field.getValue();
        assertNotNull(value);
        assertFalse(value.isEmpty());
    }

    public void test_storedField() throws Exception {
        final String index = "test_stored";

        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"minhash_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, XContentType.JSON).build());
        runner.ensureYellow(index);

        // Create mapping with stored=true
        final XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("content")
                .field("type", "text")
                .field("copy_to", "minhash_value")
                .endObject()
                .startObject("minhash_value")
                .field("type", "minhash")
                .field("store", true)
                .field("minhash_analyzer", "minhash_analyzer")
                .endObject()
                .endObject()
                .endObject();

        runner.createMapping(index, mappingBuilder);

        final IndexResponse indexResponse = runner.insert(index, "1",
                "{\"content\":\"Stored field test\"}");
        assertEquals(Result.CREATED, indexResponse.getResult());
        runner.refresh();

        final GetResponse response = runner.client().prepareGet(index, "1")
                .setStoredFields("minhash_value")
                .execute()
                .actionGet();

        assertTrue(response.isExists());
        final DocumentField field = response.getField("minhash_value");
        assertNotNull(field);
        assertNotNull(field.getValue());
    }

    public void test_notStoredField() throws Exception {
        final String index = "test_not_stored";

        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"minhash_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, XContentType.JSON).build());
        runner.ensureYellow(index);

        // Create mapping with stored=false
        final XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("content")
                .field("type", "text")
                .field("copy_to", "minhash_value")
                .endObject()
                .startObject("minhash_value")
                .field("type", "minhash")
                .field("store", false)
                .field("minhash_analyzer", "minhash_analyzer")
                .endObject()
                .endObject()
                .endObject();

        runner.createMapping(index, mappingBuilder);

        final IndexResponse indexResponse = runner.insert(index, "1",
                "{\"content\":\"Not stored field test\"}");
        assertEquals(Result.CREATED, indexResponse.getResult());
        runner.refresh();

        // Document should exist but field won't be stored
        final GetResponse response = runner.client().prepareGet(index, "1")
                .execute()
                .actionGet();

        assertTrue(response.isExists());
    }

    public void test_bitStringEncoding() throws Exception {
        final String index = "test_bitstring";

        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"minhash_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, XContentType.JSON).build());
        runner.ensureYellow(index);

        // Create mapping with bit_string=true
        final XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("content")
                .field("type", "text")
                .field("copy_to", "minhash_value")
                .endObject()
                .startObject("minhash_value")
                .field("type", "minhash")
                .field("bit_string", true)
                .field("minhash_analyzer", "minhash_analyzer")
                .endObject()
                .endObject()
                .endObject();

        runner.createMapping(index, mappingBuilder);

        final IndexResponse indexResponse = runner.insert(index, "1",
                "{\"content\":\"Binary string encoding test\"}");
        assertEquals(Result.CREATED, indexResponse.getResult());
        runner.refresh();

        final GetResponse response = runner.client().prepareGet(index, "1")
                .setStoredFields("minhash_value")
                .execute()
                .actionGet();

        assertTrue(response.isExists());
        final DocumentField field = response.getField("minhash_value");
        assertNotNull(field);
        final String value = (String) field.getValue();
        assertNotNull(value);

        // Verify it's a binary string (contains only 0s and 1s)
        assertTrue(value.matches("[01]+"));
    }

    public void test_base64Encoding() throws Exception {
        final String index = "test_base64";

        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"minhash_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, XContentType.JSON).build());
        runner.ensureYellow(index);

        // Create mapping with bit_string=false (default, base64 encoding)
        final XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("content")
                .field("type", "text")
                .field("copy_to", "minhash_value")
                .endObject()
                .startObject("minhash_value")
                .field("type", "minhash")
                .field("bit_string", false)
                .field("minhash_analyzer", "minhash_analyzer")
                .endObject()
                .endObject()
                .endObject();

        runner.createMapping(index, mappingBuilder);

        final IndexResponse indexResponse = runner.insert(index, "1",
                "{\"content\":\"Base64 encoding test\"}");
        assertEquals(Result.CREATED, indexResponse.getResult());
        runner.refresh();

        final GetResponse response = runner.client().prepareGet(index, "1")
                .setStoredFields("minhash_value")
                .execute()
                .actionGet();

        assertTrue(response.isExists());
        final DocumentField field = response.getField("minhash_value");
        assertNotNull(field);
        final String value = (String) field.getValue();
        assertNotNull(value);

        // Verify it's valid base64
        try {
            Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            fail("Value is not valid base64: " + value);
        }
    }

    public void test_nullValueHandling() throws Exception {
        final String index = "test_null";

        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"minhash_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, XContentType.JSON).build());
        runner.ensureYellow(index);

        // Create mapping with null_value
        final XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("content")
                .field("type", "text")
                .field("copy_to", "minhash_value")
                .endObject()
                .startObject("minhash_value")
                .field("type", "minhash")
                .field("null_value", "default_value")
                .field("minhash_analyzer", "minhash_analyzer")
                .endObject()
                .endObject()
                .endObject();

        runner.createMapping(index, mappingBuilder);

        // Index document with explicit null
        final IndexResponse indexResponse = runner.insert(index, "1",
                "{\"content\":null}");
        assertEquals(Result.CREATED, indexResponse.getResult());
        runner.refresh();

        final GetResponse response = runner.client().prepareGet(index, "1")
                .setStoredFields("minhash_value")
                .execute()
                .actionGet();

        assertTrue(response.isExists());
        // Since null_value is set, the field should exist with a computed hash
        final DocumentField field = response.getField("minhash_value");
        assertNotNull(field);
    }

    public void test_docValues() throws Exception {
        final String index = "test_docvalues";

        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"minhash_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, XContentType.JSON).build());
        runner.ensureYellow(index);

        // Create mapping with doc values enabled
        final XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("content")
                .field("type", "text")
                .field("copy_to", "minhash_value")
                .endObject()
                .startObject("minhash_value")
                .field("type", "minhash")
                .field("doc_values", true)
                .field("minhash_analyzer", "minhash_analyzer")
                .endObject()
                .endObject()
                .endObject();

        runner.createMapping(index, mappingBuilder);

        final IndexResponse indexResponse = runner.insert(index, "1",
                "{\"content\":\"Doc values test\"}");
        assertEquals(Result.CREATED, indexResponse.getResult());
        runner.refresh();

        final GetResponse response = runner.client().prepareGet(index, "1")
                .setStoredFields("minhash_value")
                .execute()
                .actionGet();

        assertTrue(response.isExists());
        assertNotNull(response.getField("minhash_value"));
    }

    public void test_multipleDocumentsSameContent() throws Exception {
        final String index = "test_same_content";

        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"minhash_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, XContentType.JSON).build());
        runner.ensureYellow(index);

        final XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("content")
                .field("type", "text")
                .field("copy_to", "minhash_value")
                .endObject()
                .startObject("minhash_value")
                .field("type", "minhash")
                .field("minhash_analyzer", "minhash_analyzer")
                .endObject()
                .endObject()
                .endObject();

        runner.createMapping(index, mappingBuilder);

        // Index multiple documents with same content
        final String content = "Identical content for testing";
        runner.insert(index, "1", "{\"content\":\"" + content + "\"}");
        runner.insert(index, "2", "{\"content\":\"" + content + "\"}");
        runner.insert(index, "3", "{\"content\":\"" + content + "\"}");
        runner.refresh();

        // Get all documents and verify they have same hash
        final GetResponse response1 = runner.client().prepareGet(index, "1")
                .setStoredFields("minhash_value").execute().actionGet();
        final GetResponse response2 = runner.client().prepareGet(index, "2")
                .setStoredFields("minhash_value").execute().actionGet();
        final GetResponse response3 = runner.client().prepareGet(index, "3")
                .setStoredFields("minhash_value").execute().actionGet();

        assertTrue(response1.isExists());
        assertTrue(response2.isExists());
        assertTrue(response3.isExists());

        final String hash1 = (String) response1.getField("minhash_value").getValue();
        final String hash2 = (String) response2.getField("minhash_value").getValue();
        final String hash3 = (String) response3.getField("minhash_value").getValue();

        // All hashes should be identical
        assertEquals(hash1, hash2);
        assertEquals(hash2, hash3);
    }

    public void test_differentContentsDifferentHashes() throws Exception {
        final String index = "test_different_content";

        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"minhash_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, XContentType.JSON).build());
        runner.ensureYellow(index);

        final XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("content")
                .field("type", "text")
                .field("copy_to", "minhash_value")
                .endObject()
                .startObject("minhash_value")
                .field("type", "minhash")
                .field("minhash_analyzer", "minhash_analyzer")
                .endObject()
                .endObject()
                .endObject();

        runner.createMapping(index, mappingBuilder);

        // Index documents with different content
        runner.insert(index, "1", "{\"content\":\"The quick brown fox\"}");
        runner.insert(index, "2", "{\"content\":\"Jumps over the lazy dog\"}");
        runner.insert(index, "3", "{\"content\":\"Completely different text\"}");
        runner.refresh();

        // Get all documents
        final GetResponse response1 = runner.client().prepareGet(index, "1")
                .setStoredFields("minhash_value").execute().actionGet();
        final GetResponse response2 = runner.client().prepareGet(index, "2")
                .setStoredFields("minhash_value").execute().actionGet();
        final GetResponse response3 = runner.client().prepareGet(index, "3")
                .setStoredFields("minhash_value").execute().actionGet();

        final String hash1 = (String) response1.getField("minhash_value").getValue();
        final String hash2 = (String) response2.getField("minhash_value").getValue();
        final String hash3 = (String) response3.getField("minhash_value").getValue();

        // All hashes should be different
        assertFalse(hash1.equals(hash2));
        assertFalse(hash2.equals(hash3));
        assertFalse(hash1.equals(hash3));
    }

    public void test_copyBitsToDeprecated() throws Exception {
        final String index = "test_copy_bits";

        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"minhash_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, XContentType.JSON).build());
        runner.ensureYellow(index);

        // Create mapping with deprecated copy_bits_to
        final XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("content")
                .field("type", "text")
                .field("copy_to", "minhash_value")
                .endObject()
                .startObject("minhash_value")
                .field("type", "minhash")
                .field("minhash_analyzer", "minhash_analyzer")
                .field("copy_bits_to", "bits_field")
                .endObject()
                .startObject("bits_field")
                .field("type", "keyword")
                .endObject()
                .endObject()
                .endObject();

        runner.createMapping(index, mappingBuilder);

        final IndexResponse indexResponse = runner.insert(index, "1",
                "{\"content\":\"Testing deprecated copy_bits_to\"}");
        assertEquals(Result.CREATED, indexResponse.getResult());
        runner.refresh();

        final GetResponse response = runner.client().prepareGet(index, "1")
                .setStoredFields("minhash_value")
                .execute()
                .actionGet();

        assertTrue(response.isExists());
        assertNotNull(response.getField("minhash_value"));
    }

    public void test_multipleMinHashFields() throws Exception {
        final String index = "test_multiple_fields";

        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{" +
                "\"analyzer1\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}," +
                "\"analyzer2\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"custom_minhash\"]}" +
                "}," +
                "\"filter\":{" +
                "\"custom_minhash\":{\"type\":\"minhash\",\"seed\":1000}" +
                "}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, XContentType.JSON).build());
        runner.ensureYellow(index);

        // Create mapping with multiple minhash fields
        final XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("content")
                .field("type", "text")
                .field("copy_to", Lists.newArrayList("minhash1", "minhash2"))
                .endObject()
                .startObject("minhash1")
                .field("type", "minhash")
                .field("minhash_analyzer", "analyzer1")
                .endObject()
                .startObject("minhash2")
                .field("type", "minhash")
                .field("minhash_analyzer", "analyzer2")
                .endObject()
                .endObject()
                .endObject();

        runner.createMapping(index, mappingBuilder);

        final IndexResponse indexResponse = runner.insert(index, "1",
                "{\"content\":\"Testing multiple minhash fields\"}");
        assertEquals(Result.CREATED, indexResponse.getResult());
        runner.refresh();

        final GetResponse response = runner.client().prepareGet(index, "1")
                .setStoredFields(new String[] { "minhash1", "minhash2" })
                .execute()
                .actionGet();

        assertTrue(response.isExists());
        assertNotNull(response.getField("minhash1"));
        assertNotNull(response.getField("minhash2"));

        // Different analyzers should produce different hashes
        final String hash1 = (String) response.getField("minhash1").getValue();
        final String hash2 = (String) response.getField("minhash2").getValue();
        assertFalse(hash1.equals(hash2));
    }

    public void test_parseCopyBitsFieldsSingle() {
        // Test parsing single value
        final String singleValue = "target_field";
        final String[] result = MinHashFieldMapper.parseCopyBitsFields(singleValue);
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals("target_field", result[0]);
    }

    public void test_parseCopyBitsFieldsArray() {
        // Test parsing array
        final java.util.List<String> arrayValue = java.util.Arrays.asList("field1", "field2", "field3");
        final String[] result = MinHashFieldMapper.parseCopyBitsFields(arrayValue);
        assertNotNull(result);
        assertEquals(3, result.length);
        assertEquals("field1", result[0]);
        assertEquals("field2", result[1]);
        assertEquals("field3", result[2]);
    }

    public void test_emptyContent() throws Exception {
        final String index = "test_empty_content";

        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"minhash_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, XContentType.JSON).build());
        runner.ensureYellow(index);

        final XContentBuilder mappingBuilder = XContentFactory.jsonBuilder()
                .startObject()
                .startObject("properties")
                .startObject("content")
                .field("type", "text")
                .field("copy_to", "minhash_value")
                .endObject()
                .startObject("minhash_value")
                .field("type", "minhash")
                .field("minhash_analyzer", "minhash_analyzer")
                .endObject()
                .endObject()
                .endObject();

        runner.createMapping(index, mappingBuilder);

        // Index document with empty content
        final IndexResponse indexResponse = runner.insert(index, "1", "{\"content\":\"\"}");
        assertEquals(Result.CREATED, indexResponse.getResult());
        runner.refresh();

        final GetResponse response = runner.client().prepareGet(index, "1")
                .execute()
                .actionGet();

        assertTrue(response.isExists());
    }
}
