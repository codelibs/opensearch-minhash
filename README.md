# OpenSearch MinHash Plugin

[![Java CI with Maven](https://github.com/codelibs/opensearch-minhash/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/opensearch-minhash/actions/workflows/maven.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.codelibs.opensearch/opensearch-minhash)](https://central.sonatype.com/artifact/org.codelibs.opensearch/opensearch-minhash)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

OpenSearch MinHash Plugin adds b-bit MinHash support to OpenSearch. A compact
signature is computed for each document at index time, so that near-duplicate
documents produce identical or nearly identical signatures. Because the signature is
stored as an ordinary field, it can be used for deduplication, collapsing and
grouping with the queries and aggregations OpenSearch already provides.

The plugin registers two components:

- a `minhash` token filter that reduces an analyzed token stream to a MinHash signature
- a `minhash` field type that stores the signature produced by a dedicated analyzer

## Compatibility

| Plugin Version | OpenSearch Version | Lucene Version | Java Version |
|----------------|--------------------|----------------|--------------|
| 3.8.x          | 3.8.0              | 10.5.0         | 21+          |
| 3.7.x          | 3.7.0              | 10.4.0         | 21+          |

Released versions are listed on
[Maven Central](https://central.sonatype.com/artifact/org.codelibs.opensearch/opensearch-minhash/versions).

## Installation

```bash
$OPENSEARCH_HOME/bin/opensearch-plugin install org.codelibs.opensearch:opensearch-minhash:3.8.0
```

Restart the node, then confirm that the plugin is loaded:

```bash
$OPENSEARCH_HOME/bin/opensearch-plugin list
# minhash
```

To install a locally built package instead:

```bash
mvn clean package
$OPENSEARCH_HOME/bin/opensearch-plugin install file:target/releases/opensearch-minhash-3.8.0-SNAPSHOT.zip
```

Use `opensearch-plugin remove minhash` to uninstall.

## Getting Started

A `minhash` field does not use the index analyzer. It analyzes its input with the
analyzer named by `minhash_analyzer`, whose last token filter must be `minhash`.
The usual pattern is to `copy_to` the signature field from the text field it should
summarize.

```bash
curl -XPUT 'localhost:9200/documents' -H 'Content-Type: application/json' -d '{
  "settings": {
    "analysis": {
      "analyzer": {
        "minhash_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "minhash"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "content": {
        "type": "text",
        "copy_to": "content_minhash"
      },
      "content_minhash": {
        "type": "minhash",
        "store": true,
        "minhash_analyzer": "minhash_analyzer"
      }
    }
  }
}'
```

Index a couple of documents that differ only slightly:

```bash
curl -XPUT 'localhost:9200/documents/_doc/1' -H 'Content-Type: application/json' -d '{
  "content": "OpenSearch is a distributed search and analytics engine based on Apache Lucene."
}'

curl -XPUT 'localhost:9200/documents/_doc/2' -H 'Content-Type: application/json' -d '{
  "content": "OpenSearch is a distributed search and analytics engine built on Apache Lucene."
}'
```

Because the field is stored, the signature can be read back directly:

```bash
curl -XGET 'localhost:9200/documents/_doc/1?pretty&stored_fields=content_minhash&_source=true'
```

## Configuration

### `minhash` token filter

| Parameter | Default | Description |
|-----------|---------|-------------|
| `seed`    | `0`     | Seed for the murmur3 hash functions. Signatures are only comparable when they were generated with the same seed. |
| `bit`     | `1`     | Number of bits kept from each hash value. |
| `size`    | `128`   | Number of hash functions. The resulting signature is `bit * size` bits long. |

The filter replaces the whole token stream with a single token holding the
signature, so it has to be the last filter in the chain.

Larger `size` and `bit` values make the signature a more faithful estimate of
Jaccard similarity at the cost of a longer signature.

```bash
curl -XPUT 'localhost:9200/documents' -H 'Content-Type: application/json' -d '{
  "settings": {
    "analysis": {
      "filter": {
        "custom_minhash": {
          "type": "minhash",
          "seed": 12345,
          "bit": 2,
          "size": 64
        }
      },
      "analyzer": {
        "custom_minhash_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "stop", "custom_minhash"]
        }
      }
    }
  }
}'
```

### `minhash` field type

| Parameter          | Default    | Description |
|--------------------|------------|-------------|
| `minhash_analyzer` | `standard` | Analyzer used to generate the signature. Set this to an analyzer whose last filter is `minhash`. |
| `bit_string`       | `false`    | Store the signature as a string of `0`/`1` characters instead of Base64. |
| `store`            | `false`    | Store the field so it can be returned via `stored_fields`. |
| `index`            | `true`     | Index the signature. |
| `doc_values`       | `true`     | Enable doc values, required for aggregations and collapsing. |
| `null_value`       | none       | Value substituted for a null input. |
| `copy_bits_to`     | none       | Deprecated. Copies the bit string to the named fields. |

A single source field can feed several signature fields, for example one Base64
signature for exact grouping and one bit string for inspection:

```bash
curl -XPUT 'localhost:9200/documents' -H 'Content-Type: application/json' -d '{
  "mappings": {
    "properties": {
      "content": {
        "type": "text",
        "copy_to": ["minhash_binary", "minhash_bitstring"]
      },
      "minhash_binary": {
        "type": "minhash",
        "store": true,
        "minhash_analyzer": "custom_minhash_analyzer"
      },
      "minhash_bitstring": {
        "type": "minhash",
        "store": true,
        "bit_string": true,
        "minhash_analyzer": "custom_minhash_analyzer"
      }
    }
  }
}'
```

## Examples

### Finding duplicates

Documents that share a signature are near-duplicates. A `terms` aggregation with
`min_doc_count` reports the groups that contain more than one document:

```bash
curl -XGET 'localhost:9200/documents/_search?pretty' -H 'Content-Type: application/json' -d '{
  "size": 0,
  "aggs": {
    "duplicates": {
      "terms": {
        "field": "content_minhash",
        "min_doc_count": 2,
        "size": 100
      },
      "aggs": {
        "documents": {
          "top_hits": {
            "size": 10,
            "_source": ["content"]
          }
        }
      }
    }
  }
}'
```

### Collapsing near-duplicates in results

Field collapsing keeps one representative per signature and exposes the rest as
inner hits:

```bash
curl -XGET 'localhost:9200/documents/_search?pretty' -H 'Content-Type: application/json' -d '{
  "query": { "match_all": {} },
  "collapse": {
    "field": "content_minhash",
    "inner_hits": {
      "name": "similar_docs",
      "size": 5
    }
  }
}'
```

## Building from Source

Java 21 and Maven 3.6 or later are required.

```bash
git clone https://github.com/codelibs/opensearch-minhash.git
cd opensearch-minhash
mvn clean package
```

The plugin package is written to `target/releases/`.

```bash
mvn test                              # run the test suite
mvn test -Dtest=MinHashPluginTest     # run a single test class
mvn license:format                    # apply license headers
```

## Contributing

Issues and pull requests are welcome at
[github.com/codelibs/opensearch-minhash](https://github.com/codelibs/opensearch-minhash).
Please add tests for behaviour changes and make sure `mvn test` passes before
opening a pull request.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
