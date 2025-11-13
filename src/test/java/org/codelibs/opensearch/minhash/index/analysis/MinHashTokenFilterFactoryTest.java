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
package org.codelibs.opensearch.minhash.index.analysis;

import static org.codelibs.opensearch.runner.OpenSearchRunner.newConfigs;

import java.io.StringReader;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.codelibs.opensearch.runner.OpenSearchRunner;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.Settings.Builder;
import org.opensearch.env.Environment;
import org.opensearch.index.Index;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AnalysisRegistry;

import junit.framework.TestCase;

public class MinHashTokenFilterFactoryTest extends TestCase {

    private OpenSearchRunner runner;
    private String clusterName;

    @Override
    protected void setUp() throws Exception {
        clusterName = "es-minhash-test-" + System.currentTimeMillis();
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

    public void test_defaultSettings() throws Exception {
        final String index = "test_index";

        // Create index with default minhash filter
        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"test_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}}," +
                "\"filter\":{}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, org.opensearch.common.xcontent.XContentType.JSON).build());
        runner.ensureYellow(index);

        // Verify index was created successfully
        assertTrue(runner.indexExists(index));

        // Analyze text with the minhash analyzer
        final String text = "This is a test document for minhash";
        final org.opensearch.action.admin.indices.analyze.AnalyzeAction.Response response =
            runner.client().admin().indices()
                .prepareAnalyze(index, text)
                .setAnalyzer("test_analyzer")
                .execute()
                .actionGet();

        // MinHash should produce hash tokens
        assertNotNull(response);
        assertNotNull(response.getTokens());
        assertTrue(response.getTokens().size() > 0);
    }

    public void test_customSettings() throws Exception {
        final String index = "test_custom_index";

        // Create index with custom minhash settings
        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"custom_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"custom_minhash\"]}}," +
                "\"filter\":{\"custom_minhash\":{\"type\":\"minhash\",\"bit\":2,\"size\":64,\"seed\":12345}}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, org.opensearch.common.xcontent.XContentType.JSON).build());
        runner.ensureYellow(index);

        assertTrue(runner.indexExists(index));

        // Analyze text with custom minhash settings
        final String text = "Custom settings test";
        final org.opensearch.action.admin.indices.analyze.AnalyzeAction.Response response =
            runner.client().admin().indices()
                .prepareAnalyze(index, text)
                .setAnalyzer("custom_analyzer")
                .execute()
                .actionGet();

        assertNotNull(response);
        assertNotNull(response.getTokens());
        assertTrue(response.getTokens().size() > 0);
    }

    public void test_differentBitSettings() throws Exception {
        final String index = "test_bit_index";

        // Test with 1-bit (default)
        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{" +
                "\"analyzer_1bit\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash_1bit\"]}," +
                "\"analyzer_2bit\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash_2bit\"]}" +
                "}," +
                "\"filter\":{" +
                "\"minhash_1bit\":{\"type\":\"minhash\",\"bit\":1,\"size\":128}," +
                "\"minhash_2bit\":{\"type\":\"minhash\",\"bit\":2,\"size\":128}" +
                "}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, org.opensearch.common.xcontent.XContentType.JSON).build());
        runner.ensureYellow(index);

        final String text = "Testing different bit settings";

        // Test 1-bit analyzer
        final org.opensearch.action.admin.indices.analyze.AnalyzeAction.Response response1bit =
            runner.client().admin().indices()
                .prepareAnalyze(index, text)
                .setAnalyzer("analyzer_1bit")
                .execute()
                .actionGet();

        assertNotNull(response1bit);
        assertTrue(response1bit.getTokens().size() > 0);

        // Test 2-bit analyzer
        final org.opensearch.action.admin.indices.analyze.AnalyzeAction.Response response2bit =
            runner.client().admin().indices()
                .prepareAnalyze(index, text)
                .setAnalyzer("analyzer_2bit")
                .execute()
                .actionGet();

        assertNotNull(response2bit);
        assertTrue(response2bit.getTokens().size() > 0);
    }

    public void test_differentSizeSettings() throws Exception {
        final String index = "test_size_index";

        // Test with different hash sizes
        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{" +
                "\"analyzer_size32\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash_32\"]}," +
                "\"analyzer_size128\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash_128\"]}," +
                "\"analyzer_size256\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash_256\"]}" +
                "}," +
                "\"filter\":{" +
                "\"minhash_32\":{\"type\":\"minhash\",\"size\":32}," +
                "\"minhash_128\":{\"type\":\"minhash\",\"size\":128}," +
                "\"minhash_256\":{\"type\":\"minhash\",\"size\":256}" +
                "}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, org.opensearch.common.xcontent.XContentType.JSON).build());
        runner.ensureYellow(index);

        final String text = "Testing different size settings";

        // Test size 32
        final org.opensearch.action.admin.indices.analyze.AnalyzeAction.Response response32 =
            runner.client().admin().indices()
                .prepareAnalyze(index, text)
                .setAnalyzer("analyzer_size32")
                .execute()
                .actionGet();

        assertNotNull(response32);
        assertTrue(response32.getTokens().size() > 0);

        // Test size 128 (default)
        final org.opensearch.action.admin.indices.analyze.AnalyzeAction.Response response128 =
            runner.client().admin().indices()
                .prepareAnalyze(index, text)
                .setAnalyzer("analyzer_size128")
                .execute()
                .actionGet();

        assertNotNull(response128);
        assertTrue(response128.getTokens().size() > 0);

        // Test size 256
        final org.opensearch.action.admin.indices.analyze.AnalyzeAction.Response response256 =
            runner.client().admin().indices()
                .prepareAnalyze(index, text)
                .setAnalyzer("analyzer_size256")
                .execute()
                .actionGet();

        assertNotNull(response256);
        assertTrue(response256.getTokens().size() > 0);
    }

    public void test_seedDeterminism() throws Exception {
        final String index = "test_seed_index";

        // Create two analyzers with different seeds
        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{" +
                "\"analyzer_seed0\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash_seed0\"]}," +
                "\"analyzer_seed1000\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash_seed1000\"]}" +
                "}," +
                "\"filter\":{" +
                "\"minhash_seed0\":{\"type\":\"minhash\",\"seed\":0}," +
                "\"minhash_seed1000\":{\"type\":\"minhash\",\"seed\":1000}" +
                "}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, org.opensearch.common.xcontent.XContentType.JSON).build());
        runner.ensureYellow(index);

        final String text = "Testing seed determinism";

        // Analyze with seed 0
        final org.opensearch.action.admin.indices.analyze.AnalyzeAction.Response response1 =
            runner.client().admin().indices()
                .prepareAnalyze(index, text)
                .setAnalyzer("analyzer_seed0")
                .execute()
                .actionGet();

        // Analyze same text again with seed 0
        final org.opensearch.action.admin.indices.analyze.AnalyzeAction.Response response2 =
            runner.client().admin().indices()
                .prepareAnalyze(index, text)
                .setAnalyzer("analyzer_seed0")
                .execute()
                .actionGet();

        // Results should be identical for same seed
        assertNotNull(response1);
        assertNotNull(response2);
        assertEquals(response1.getTokens().size(), response2.getTokens().size());

        if (response1.getTokens().size() > 0 && response2.getTokens().size() > 0) {
            assertEquals(response1.getTokens().get(0).getTerm(), response2.getTokens().get(0).getTerm());
        }

        // Analyze with different seed
        final org.opensearch.action.admin.indices.analyze.AnalyzeAction.Response response3 =
            runner.client().admin().indices()
                .prepareAnalyze(index, text)
                .setAnalyzer("analyzer_seed1000")
                .execute()
                .actionGet();

        assertNotNull(response3);
        // Different seeds should produce different results
        if (response1.getTokens().size() > 0 && response3.getTokens().size() > 0) {
            assertFalse(response1.getTokens().get(0).getTerm().equals(response3.getTokens().get(0).getTerm()));
        }
    }

    public void test_emptyText() throws Exception {
        final String index = "test_empty_index";

        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"test_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\",\"filter\":[\"minhash\"]}}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, org.opensearch.common.xcontent.XContentType.JSON).build());
        runner.ensureYellow(index);

        // Analyze empty text
        final org.opensearch.action.admin.indices.analyze.AnalyzeAction.Response response =
            runner.client().admin().indices()
                .prepareAnalyze(index, "")
                .setAnalyzer("test_analyzer")
                .execute()
                .actionGet();

        assertNotNull(response);
        // Empty text should produce no tokens or empty tokens
        assertTrue(response.getTokens().size() == 0 || response.getTokens().get(0).getTerm().isEmpty());
    }

    public void test_multipleFiltersInChain() throws Exception {
        final String index = "test_chain_index";

        // Test minhash with other filters in chain
        final String indexSettings = "{\"index\":{\"analysis\":{" +
                "\"analyzer\":{\"chain_analyzer\":{\"type\":\"custom\",\"tokenizer\":\"standard\"," +
                "\"filter\":[\"lowercase\",\"stop\",\"minhash\"]}}," +
                "\"filter\":{}}}}";

        runner.createIndex(index, Settings.builder()
                .loadFromSource(indexSettings, org.opensearch.common.xcontent.XContentType.JSON).build());
        runner.ensureYellow(index);

        final String text = "The Quick Brown Fox Jumps Over The Lazy Dog";
        final org.opensearch.action.admin.indices.analyze.AnalyzeAction.Response response =
            runner.client().admin().indices()
                .prepareAnalyze(index, text)
                .setAnalyzer("chain_analyzer")
                .execute()
                .actionGet();

        assertNotNull(response);
        assertNotNull(response.getTokens());
        assertTrue(response.getTokens().size() > 0);
    }
}
