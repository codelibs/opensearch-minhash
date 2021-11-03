package org.codelibs.opensearch.minhash.index.mapper;

import static org.opensearch.common.xcontent.support.XContentMapValues.isArray;
import static org.opensearch.common.xcontent.support.XContentMapValues.nodeStringValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.BytesRef;
import org.codelibs.minhash.MinHash;
import org.opensearch.common.lucene.Lucene;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.index.analysis.NamedAnalyzer;
import org.opensearch.index.mapper.FieldMapper;
import org.opensearch.index.mapper.KeywordFieldMapper;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.index.mapper.Mapper;
import org.opensearch.index.mapper.Mapper.TypeParser.ParserContext;
import org.opensearch.index.mapper.MapperParsingException;
import org.opensearch.index.mapper.ParametrizedFieldMapper;
import org.opensearch.index.mapper.ParseContext;

public class MinHashFieldMapper extends ParametrizedFieldMapper {

    public static final String CONTENT_TYPE = "minhash";

    public static class Defaults {
        public static final FieldType FIELD_TYPE = new FieldType();

        static {
            FIELD_TYPE.setTokenized(false);
            FIELD_TYPE.setOmitNorms(true);
            FIELD_TYPE.setIndexOptions(IndexOptions.DOCS);
            FIELD_TYPE.freeze();
        }
    }

    public static class MinHashField extends Field {
        public MinHashField(final String field, final BytesRef term,
                final FieldType ft) {
            super(field, term, ft);
        }
    }

    private static MinHashFieldMapper toType(final FieldMapper in) {
        return (MinHashFieldMapper) in;
    }

    public static class Builder extends ParametrizedFieldMapper.Builder {

        private final Parameter<Boolean> indexed = Parameter
                .indexParam(m -> toType(m).indexed, true);

        private final Parameter<Boolean> hasDocValues = Parameter
                .docValuesParam(m -> toType(m).hasDocValues, true);

        private final Parameter<Boolean> stored = Parameter
                .storeParam(m -> toType(m).stored, true);

        private final Parameter<String> nullValue = Parameter.stringParam(
                "null_value", false, m -> toType(m).nullValue, null);

        private final Parameter<Boolean> bitString = Parameter.boolParam(
                "bit_string", false, m -> toType(m).bitString, false);

        private final Parameter<Map<String, String>> meta = Parameter
                .metaParam();

        private final Parameter<String> minhashAnalyzer = Parameter
                .stringParam("minhash_analyzer", true, m -> {
                    final NamedAnalyzer minhashAnalyzer = toType(
                            m).minhashAnalyzer;
                    if (minhashAnalyzer != null) {
                        return minhashAnalyzer.name();
                    }
                    return "standard";
                }, "standard");

        @Deprecated
        private final Parameter<String[]> copyBitsTo = new Parameter<>(
                "copy_bits_to", true, () -> new String[0],
                (n, c, o) -> parseCopyBitsFields(o), m -> new String[0]);

        private final ParserContext parserContext;

        private NamedAnalyzer mergedAnalyzer;

        private KeywordFieldMapper.Builder keywordFieldBuilder;

        public Builder(final String name,
                final ParserContext parserContext, final KeywordFieldMapper.Builder keywordFieldBuilder) {
            super(name);
            this.parserContext = parserContext;
            this.keywordFieldBuilder = keywordFieldBuilder;
        }

        @Override
        public List<Parameter<?>> getParameters() {
            return Arrays.asList(meta, indexed, stored, hasDocValues, nullValue,
                    bitString, minhashAnalyzer, copyBitsTo);
        }

        @Override
        public Builder init(final FieldMapper initializer) {
            super.init(initializer);
            if (initializer instanceof MinHashFieldMapper) {
                final MinHashFieldMapper mapper = (MinHashFieldMapper) initializer;
                this.indexed.setValue(mapper.indexed);
                this.hasDocValues.setValue(mapper.hasDocValues);
                this.nullValue.setValue(mapper.nullValue);
                this.bitString.setValue(mapper.bitString);
                this.mergedAnalyzer = mapper.minhashAnalyzer;
                this.keywordFieldBuilder = mapper.keywordFieldBuilder;
            }
            return this;
        }

        public Builder minhashAnalyzer(final NamedAnalyzer minhashAnalyzer) {
            this.mergedAnalyzer = minhashAnalyzer;
            return this;
        }

        private NamedAnalyzer minhashAnalyzer() {
            if (mergedAnalyzer != null) {
                return mergedAnalyzer;
            }
            if (parserContext != null) {
                return parserContext.getIndexAnalyzers()
                        .get(minhashAnalyzer.getValue());
            }
            return null;
        }

        private KeywordFieldMapper.KeywordFieldType buildFieldType(
                final BuilderContext context, final FieldType fieldType) {
            final NamedAnalyzer normalizer = Lucene.KEYWORD_ANALYZER;
            final NamedAnalyzer searchAnalyzer = Lucene.KEYWORD_ANALYZER;
            return new KeywordFieldMapper.KeywordFieldType(
                    buildFullName(context), fieldType, normalizer,
                    searchAnalyzer, keywordFieldBuilder);
        }

        @Override
        public MinHashFieldMapper build(final BuilderContext context) {
            final FieldType fieldtype = new FieldType(
                    MinHashFieldMapper.Defaults.FIELD_TYPE);
            fieldtype.setIndexOptions(
                    indexed.getValue() ? IndexOptions.DOCS : IndexOptions.NONE);
            fieldtype.setStored(this.stored.getValue());
            return new MinHashFieldMapper(name, fieldtype,
                    buildFieldType(context, fieldtype),
                    multiFieldsBuilder.build(this, context), copyTo.build(),
                    this, minhashAnalyzer(), keywordFieldBuilder);
        }
    }

    public static class TypeParser implements Mapper.TypeParser {
        @Override
        public MinHashFieldMapper.Builder parse(final String name,
                final Map<String, Object> node,
                final ParserContext parserContext)
                throws MapperParsingException {
            final Map<String, Object> keywordNode = new HashMap<>(node);
            keywordNode.remove("minhash_analyzer");
            keywordNode.remove("copy_bits_to");
            keywordNode.remove("bit_string");
            final KeywordFieldMapper.Builder keywordFieldBuilder = (KeywordFieldMapper.Builder) KeywordFieldMapper.PARSER
                    .parse(name, keywordNode, parserContext);

            final MinHashFieldMapper.Builder builder = new MinHashFieldMapper.Builder(
                    name, parserContext, keywordFieldBuilder);
            builder.parse(name, parserContext, node);
            return builder;
        }
    }

    @Deprecated
    public static String[] parseCopyBitsFields(final Object propNode) {
        if (isArray(propNode)) {
            @SuppressWarnings("unchecked")
            final List<Object> nodeList = (List<Object>) propNode;
            return nodeList.stream().map(o -> nodeStringValue(o, null))
                    .filter(s -> s != null).toArray(n -> new String[n]);
        }
        return new String[] { nodeStringValue(propNode, null) };
    }

    private final boolean indexed;

    private final boolean stored;

    private final boolean hasDocValues;

    private final String nullValue;

    private final boolean bitString;

    private final NamedAnalyzer minhashAnalyzer;

    private final FieldType fieldType;

    private final KeywordFieldMapper.Builder keywordFieldBuilder;

    protected MinHashFieldMapper(final String simpleName,
            final FieldType fieldType, final MappedFieldType mappedFieldType,
            final MultiFields multiFields, final CopyTo copyTo,
            final Builder builder, final NamedAnalyzer minhashAnalyzer,
            final KeywordFieldMapper.Builder keywordFieldBuilder) {
        super(simpleName, mappedFieldType, multiFields, copyTo);
        this.indexed = builder.indexed.getValue();
        this.stored = builder.stored.getValue();
        this.hasDocValues = builder.hasDocValues.getValue();
        this.nullValue = builder.nullValue.getValue();
        this.bitString = builder.bitString.getValue();
        this.minhashAnalyzer = minhashAnalyzer;
        this.fieldType = fieldType;
        this.keywordFieldBuilder = keywordFieldBuilder;
    }

    @Override
    protected void parseCreateField(final ParseContext context)
            throws IOException {
        if (!indexed && !stored && !hasDocValues) {
            return;
        }

        String value;
        final XContentParser parser = context.parser();
        if (parser.currentToken() == XContentParser.Token.VALUE_NULL) {
            value = nullValue;
        } else {
            value = parser.textOrNull();
        }

        if (value == null) {
            return;
        }

        final byte[] minhashValue = MinHash.calculate(minhashAnalyzer, value);
        final String stringValue;
        if (bitString) {
            stringValue = MinHash.toBinaryString(minhashValue);
        } else {
            stringValue = new String(Base64.getEncoder().encode(minhashValue),
                    StandardCharsets.UTF_8);
        }

        if (indexed || stored) {
            final IndexableField field = new MinHashField(fieldType().name(),
                    new BytesRef(stringValue), fieldType);
            context.doc().add(field);

            if (!hasDocValues) {
                createFieldNamesField(context);
            }
        }

        if (hasDocValues) {
            final BytesRef binaryValue = new BytesRef(stringValue);
            context.doc().add(new SortedSetDocValuesField(fieldType().name(),
                    binaryValue));
        }
    }

    @Override
    public ParametrizedFieldMapper.Builder getMergeBuilder() {
        return new MinHashFieldMapper.Builder(simpleName(), null, null).init(this);
    }

    @Override
    protected String contentType() {
        return CONTENT_TYPE;
    }
}
