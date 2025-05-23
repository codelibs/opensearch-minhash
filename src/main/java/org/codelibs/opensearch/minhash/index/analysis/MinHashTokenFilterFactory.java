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

import org.apache.lucene.analysis.TokenStream;
import org.codelibs.minhash.MinHash;
import org.codelibs.minhash.analysis.MinHashTokenFilter;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.index.IndexSettings;
import org.opensearch.index.analysis.AbstractTokenFilterFactory;

import com.google.common.hash.HashFunction;

public class MinHashTokenFilterFactory extends AbstractTokenFilterFactory {

    private final int hashBit;

    private final HashFunction[] hashFunctions;

    public MinHashTokenFilterFactory(final IndexSettings indexSettings, final Environment environment, final String name, final Settings settings) {
        super(indexSettings, name, settings);

        hashBit = settings.getAsInt("bit", 1);
        final int numOfHash = settings.getAsInt("size", 128);
        final int seed = settings.getAsInt("seed", 0);

        hashFunctions = MinHash.createHashFunctions(seed, numOfHash);

        if (logger.isDebugEnabled()) {
            logger.debug("Index:{} -> {}-bit minhash with {} murmur3({}) functions.", indexSettings.getIndex(), hashBit, numOfHash, seed);
        }
    }

    @Override
    public TokenStream create(final TokenStream tokenStream) {
        return new MinHashTokenFilter(tokenStream, hashFunctions, hashBit);
    }
}
