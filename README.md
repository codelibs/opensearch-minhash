OpenSearch MinHash Plugin
[![Java CI with Maven](https://github.com/codelibs/opensearch-minhash/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/opensearch-minhash/actions/workflows/maven.yml)
=======================

## Overview

The OpenSearch MinHash Plugin provides b-bit MinHash algorithm support for OpenSearch, enabling efficient similarity detection and approximate nearest neighbor search. This plugin is particularly useful for:

- **Document Deduplication**: Identify similar or duplicate documents in large datasets
- **Content Clustering**: Group similar documents based on MinHash signatures
- **Similarity Search**: Find documents with similar content patterns
- **Large-scale Analysis**: Efficient similarity computation for massive document collections

Using the custom field type and token filter provided by this plugin, you can automatically generate and store MinHash values for your documents during indexing.

## Version Compatibility

| Plugin Version | OpenSearch Version | Java Version |
|---------------|--------------------|--------------|
| 3.2.x         | 3.2.0              | 21+          |

[All Versions in Maven Repository](https://repo1.maven.org/maven2/org/codelibs/opensearch/opensearch-minhash/)

### Issues/Questions

Please file an [issue](https://github.com/codelibs/opensearch-minhash/issues "issue").

## Installation

### From Maven Repository
```bash
$OPENSEARCH_HOME/bin/opensearch-plugin install org.codelibs.opensearch:opensearch-minhash:3.2.0
```

### From Local Build
```bash
# Build the plugin
mvn clean package

# Install from local file
$OPENSEARCH_HOME/bin/opensearch-plugin install file:target/releases/opensearch-minhash-3.2.1-SNAPSHOT.zip
```

## Getting Started

### Basic Configuration

Create an index with MinHash analyzer and field mapping:

```bash
curl -XPUT 'localhost:9200/my_index' -H 'Content-Type: application/json' -d '{
  "settings": {
    "analysis": {
      "analyzer": {
        "minhash_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["minhash"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "message": {
        "type": "text",
        "copy_to": "minhash_value"
      },
      "minhash_value": {
        "type": "minhash",
        "store": true,
        "minhash_analyzer": "minhash_analyzer"
      }
    }
  }
}'
```

**Important**: The minhash filter must be the last filter in the analyzer chain.

### Advanced Configuration

For more control over the MinHash algorithm parameters:

```bash
curl -XPUT 'localhost:9200/my_advanced_index' -H 'Content-Type: application/json' -d '{
  "settings": {
    "analysis": {
      "filter": {
        "my_minhash_filter": {
          "type": "minhash",
          "seed": 100,
          "bit": 2,
          "size": 32
        }
      },
      "analyzer": {
        "minhash_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["my_minhash_filter"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "content": {
        "type": "text",
        "copy_to": ["minhash_binary", "minhash_bitstring"]
      },
      "minhash_binary": {
        "type": "minhash",
        "store": true,
        "minhash_analyzer": "minhash_analyzer"
      },
      "minhash_bitstring": {
        "type": "minhash",
        "store": true,
        "bit_string": true,
        "minhash_analyzer": "minhash_analyzer"
      }
    }
  }
}'
```

### Field Type Configuration

The `minhash` field type supports the following parameters:

- **`minhash_analyzer`** (required): The analyzer to use for generating MinHash values
- **`store`** (optional, default: false): Whether to store the MinHash value
- **`bit_string`** (optional, default: false): Store as bit string instead of binary
- **`copy_bits_to`** (optional): Copy bit string representation to another field

## Usage Examples

### Adding Documents

Add a document with automatic MinHash generation:

```bash
curl -XPUT "localhost:9200/my_index/_doc/1" -H 'Content-Type: application/json' -d '{
  "message": "OpenSearch is a distributed search and analytics engine based on Apache Lucene."
}'
```

### Retrieving MinHash Values

Get the document with its MinHash value:

```bash
curl -XGET "localhost:9200/my_index/_doc/1?pretty&stored_fields=minhash_value,_source"
```

Example response:
```json
{
  "_index": "my_index",
  "_type": "_doc", 
  "_id": "1",
  "_version": 1,
  "found": true,
  "_source": {
    "message": "OpenSearch is a distributed search and analytics engine based on Apache Lucene."
  },
  "fields": {
    "minhash_value": ["KV5rsUfZpcZdVojpG8mHLA=="]
  }
}
```

### Similarity Search Using MinHash

Use MinHash values for document deduplication and similarity search:

```bash
# Search for documents with similar MinHash values
curl -XGET "localhost:9200/my_index/_search?pretty" -H 'Content-Type: application/json' -d '{
  "query": {
    "bool": {
      "must": [
        {
          "term": {
            "minhash_value": "KV5rsUfZpcZdVojpG8mHLA=="
          }
        }
      ]
    }
  }
}'
```

### Collapse Duplicates

Use MinHash values to collapse similar documents:

```bash
curl -XGET "localhost:9200/my_index/_search?pretty" -H 'Content-Type: application/json' -d '{
  "query": {
    "match_all": {}
  },
  "collapse": {
    "field": "minhash_value",
    "inner_hits": {
      "name": "similar_docs",
      "size": 3
    }
  }
}'
```

## Configuration Reference

### MinHash Token Filter Parameters

The MinHash token filter supports the following configuration parameters:

| Parameter | Type    | Default | Description |
|-----------|---------|---------|-------------|
| `type`    | string  | -       | Must be "minhash" |
| `seed`    | integer | 0       | Seed value for hash functions |
| `bit`     | integer | 1       | Number of bits per hash value |
| `size`    | integer | 128     | Number of hash functions to use |

Example configuration:
```json
{
  "settings": {
    "analysis": {
      "filter": {
        "custom_minhash": {
          "type": "minhash",
          "seed": 100,
          "bit": 2,
          "size": 32
        }
      },
      "analyzer": {
        "minhash_analyzer": {
          "type": "custom", 
          "tokenizer": "standard",
          "filter": ["custom_minhash"]
        }
      }
    }
  }
}
```

### MinHash Field Mapper Parameters

| Parameter          | Type    | Default | Description |
|-------------------|---------|---------|-------------|
| `type`            | string  | -       | Must be "minhash" |
| `minhash_analyzer`| string  | -       | Analyzer to use for MinHash generation |
| `store`           | boolean | false   | Whether to store the field value |
| `bit_string`      | boolean | false   | Store as bit string instead of binary |
| `copy_bits_to`    | array   | -       | Fields to copy bit string representation to |

## Development

### Building the Plugin

```bash
# Clean build
mvn clean package

# Skip tests for faster build
mvn package -DskipTests=true

# Create distribution zip
mvn clean package assembly:single
```

### Testing

```bash
# Run unit tests
mvn test
```

### Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Run the full test suite
6. Submit a pull request

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

