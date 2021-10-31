package org.codelibs.opensearch.minhash;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.codelibs.opensearch.minhash.index.analysis.MinHashTokenFilterFactory;
import org.codelibs.opensearch.minhash.index.mapper.MinHashFieldMapper;
import org.opensearch.index.analysis.TokenFilterFactory;
import org.opensearch.index.mapper.Mapper;
import org.opensearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.opensearch.plugins.AnalysisPlugin;
import org.opensearch.plugins.MapperPlugin;
import org.opensearch.plugins.Plugin;

public class MinHashPlugin extends Plugin implements MapperPlugin, AnalysisPlugin {

    @Override
    public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
        final Map<String, AnalysisProvider<TokenFilterFactory>> extra = new HashMap<>();
        extra.put("minhash", MinHashTokenFilterFactory::new);
        return extra;
    }

    @Override
    public Map<String, Mapper.TypeParser> getMappers() {
        return Collections.<String, Mapper.TypeParser> singletonMap(MinHashFieldMapper.CONTENT_TYPE, new MinHashFieldMapper.TypeParser());
    }
}
