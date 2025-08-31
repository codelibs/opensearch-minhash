# OpenSearch MinHash Plugin

[![Java CI with Maven](https://github.com/codelibs/opensearch-minhash/actions/workflows/maven.yml/badge.svg)](https://github.com/codelibs/opensearch-minhash/actions/workflows/maven.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.codelibs.opensearch/opensearch-minhash/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.codelibs.opensearch/opensearch-minhash)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A high-performance OpenSearch plugin that provides **b-bit MinHash algorithm** support for efficient similarity detection and approximate nearest neighbor search. This plugin enables powerful document deduplication, content clustering, and large-scale similarity analysis directly within OpenSearch indices.

## ✨ Key Features

- **🚀 High Performance**: Efficient b-bit MinHash implementation with configurable parameters
- **🔍 Similarity Detection**: Advanced document similarity detection using MinHash signatures
- **📊 Deduplication**: Automatic duplicate document identification and removal
- **🎯 Clustering Support**: Group similar documents based on MinHash signatures
- **⚡ Real-time Processing**: Generate MinHash values during document indexing
- **🛠️ Flexible Configuration**: Customizable hash functions, bit sizes, and seed values
- **📈 Scalable**: Optimized for large-scale document collections

## 🏗️ Architecture

The plugin integrates seamlessly with OpenSearch's analysis pipeline through three core components:

- **`MinHashPlugin`**: Main plugin entry point that registers token filters and field mappers
- **`MinHashFieldMapper`**: Implements the "minhash" field type for storing binary MinHash values
- **`MinHashTokenFilterFactory`**: Provides the "minhash" token filter for analysis pipelines

## 📋 Version Compatibility

| Plugin Version | OpenSearch Version | Java Version | Lucene Version |
|---------------|--------------------|--------------|----------------|
| 3.2.x         | 3.2.0              | 21+          | 10.2.2         |

[📦 All Versions in Maven Repository](https://repo1.maven.org/maven2/org/codelibs/opensearch/opensearch-minhash/)

## 🚀 Installation

### Quick Install from Maven Central

```bash
$OPENSEARCH_HOME/bin/opensearch-plugin install org.codelibs.opensearch:opensearch-minhash:3.2.0
```

### Build and Install from Source

```bash
# Clone the repository
git clone https://github.com/codelibs/opensearch-minhash.git
cd opensearch-minhash

# Build the plugin
mvn clean package

# Install from local build
$OPENSEARCH_HOME/bin/opensearch-plugin install file:target/releases/opensearch-minhash-3.2.1-SNAPSHOT.zip

# Restart OpenSearch
$OPENSEARCH_HOME/bin/opensearch-node restart
```

### Verify Installation

```bash
# Check installed plugins
$OPENSEARCH_HOME/bin/opensearch-plugin list

# Expected output should include:
# opensearch-minhash
```

## 🎯 Quick Start

### Basic Usage Example

Create an index with MinHash field mapping and analyzer:

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
      "title": {
        "type": "text",
        "copy_to": "title_minhash"
      },
      "content": {
        "type": "text",
        "copy_to": "content_minhash"
      },
      "title_minhash": {
        "type": "minhash",
        "store": true,
        "minhash_analyzer": "minhash_analyzer"
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

### Add Documents with Automatic MinHash Generation

```bash
# Add first document
curl -XPUT "localhost:9200/documents/_doc/1" -H 'Content-Type: application/json' -d '{
  "title": "OpenSearch Tutorial",
  "content": "OpenSearch is a distributed search and analytics engine based on Apache Lucene."
}'

# Add similar document
curl -XPUT "localhost:9200/documents/_doc/2" -H 'Content-Type: application/json' -d '{
  "title": "OpenSearch Guide", 
  "content": "OpenSearch is a distributed search and analytics engine built on Apache Lucene."
}'
```

### Retrieve Documents with MinHash Values

```bash
curl -XGET "localhost:9200/documents/_doc/1?pretty&stored_fields=title_minhash,content_minhash,_source"
```

## ⚙️ Advanced Configuration

### Custom MinHash Filter Parameters

```bash
curl -XPUT 'localhost:9200/advanced_documents' -H 'Content-Type: application/json' -d '{
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
  },
  "mappings": {
    "properties": {
      "text": {
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

### Multiple Analyzers for Different Use Cases

```bash
curl -XPUT 'localhost:9200/multi_analyzer_index' -H 'Content-Type: application/json' -d '{
  "settings": {
    "analysis": {
      "filter": {
        "fast_minhash": {
          "type": "minhash",
          "size": 32,
          "bit": 1
        },
        "precise_minhash": {
          "type": "minhash", 
          "size": 256,
          "bit": 2
        }
      },
      "analyzer": {
        "fast_analyzer": {
          "type": "custom",
          "tokenizer": "keyword",
          "filter": ["fast_minhash"]
        },
        "precise_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "precise_minhash"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "content": {
        "type": "text",
        "copy_to": ["fast_hash", "precise_hash"]
      },
      "fast_hash": {
        "type": "minhash",
        "minhash_analyzer": "fast_analyzer"
      },
      "precise_hash": {
        "type": "minhash", 
        "minhash_analyzer": "precise_analyzer"
      }
    }
  }
}'
```

## 🔧 Configuration Reference

### MinHash Token Filter Parameters

| Parameter | Type    | Default | Range | Description |
|-----------|---------|---------|--------|-------------|
| `type`    | string  | -       | -      | Must be "minhash" |
| `seed`    | integer | 0       | 0+     | Seed value for hash functions |
| `bit`     | integer | 1       | 1-8    | Number of bits per hash value |
| `size`    | integer | 128     | 1-1024 | Number of hash functions to use |

**⚠️ Important**: The minhash filter must be the **last filter** in the analyzer chain.

### MinHash Field Mapper Parameters

| Parameter          | Type    | Default | Description |
|-------------------|---------|---------|-------------|
| `type`            | string  | -       | Must be "minhash" |
| `minhash_analyzer`| string  | -       | **Required**: Analyzer for MinHash generation |
| `store`           | boolean | false   | Whether to store the field value |
| `bit_string`      | boolean | false   | Store as bit string instead of base64 |
| `copy_bits_to`    | array   | -       | **Deprecated**: Fields to copy bit string to |

## 📊 Practical Use Cases

### 1. Document Deduplication

Find and remove duplicate documents using MinHash signatures:

```bash
# Search for documents with identical MinHash values
curl -XGET "localhost:9200/documents/_search?pretty" -H 'Content-Type: application/json' -d '{
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
            "_source": ["title", "content"]
          }
        }
      }
    }
  }
}'
```

### 2. Similarity-based Grouping

Collapse similar documents using field collapsing:

```bash
curl -XGET "localhost:9200/documents/_search?pretty" -H 'Content-Type: application/json' -d '{
  "query": {
    "match_all": {}
  },
  "collapse": {
    "field": "content_minhash",
    "inner_hits": {
      "name": "similar_docs",
      "size": 5,
      "sort": [{"_score": {"order": "desc"}}]
    }
  },
  "sort": [{"_score": {"order": "desc"}}]
}'
```

### 3. Content Clustering Analysis

Analyze content distribution using MinHash aggregations:

```bash
curl -XGET "localhost:9200/documents/_search?pretty" -H 'Content-Type: application/json' -d '{
  "size": 0,
  "aggs": {
    "content_clusters": {
      "terms": {
        "field": "content_minhash",
        "size": 50
      },
      "aggs": {
        "cluster_size": {
          "value_count": {
            "field": "content_minhash"
          }
        },
        "sample_docs": {
          "top_hits": {
            "size": 3,
            "_source": ["title"]
          }
        }
      }
    }
  }
}'
```

## 🛠️ Development

### Prerequisites

- **Java 21+**: OpenJDK or Oracle JDK
- **Maven 3.6+**: Build automation
- **OpenSearch 3.2.0**: Target platform

### Project Structure

```
opensearch-minhash/
├── src/main/java/org/codelibs/opensearch/minhash/
│   ├── MinHashPlugin.java                     # Plugin entry point
│   ├── index/
│   │   ├── analysis/
│   │   │   └── MinHashTokenFilterFactory.java # Token filter implementation  
│   │   └── mapper/
│   │       └── MinHashFieldMapper.java        # Field mapper implementation
│   └── plugin-metadata/
│       └── plugin-descriptor.properties       # Plugin metadata
├── src/test/java/                             # Unit tests
├── pom.xml                                    # Maven configuration
└── README.md                                  # This file
```

### Building the Plugin

```bash
# Full clean build
mvn clean package

# Skip tests for faster builds
mvn package -DskipTests=true

# Create distribution zip
mvn clean package assembly:single

# Format license headers
mvn license:format
```

### Running Tests

```bash
# Run all unit tests
mvn test

# Run specific test class
mvn test -Dtest=MinHashPluginTest

# Run tests with verbose output
mvn test -X
```

### Development Workflow

1. **Setup Development Environment**:
   ```bash
   git clone https://github.com/codelibs/opensearch-minhash.git
   cd opensearch-minhash
   mvn clean compile
   ```

2. **Make Changes**: Edit source files in `src/main/java/`

3. **Test Changes**: 
   ```bash
   mvn test
   mvn integration-test  # If integration tests exist
   ```

4. **Build Plugin**:
   ```bash
   mvn clean package
   ```

5. **Install for Testing**:
   ```bash
   $OPENSEARCH_HOME/bin/opensearch-plugin remove opensearch-minhash  # Remove old version
   $OPENSEARCH_HOME/bin/opensearch-plugin install file:target/releases/opensearch-minhash-3.2.1-SNAPSHOT.zip
   ```

### Code Style Guidelines

- Follow standard Java naming conventions
- Use meaningful variable and method names
- Add comprehensive Javadoc for public APIs
- Include unit tests for new functionality
- Maintain license headers (automatically managed by Maven)

## 🤝 Contributing

We welcome contributions! Please follow these guidelines:

### Getting Started

1. **Fork the repository** on GitHub
2. **Create a feature branch**: `git checkout -b feature/my-new-feature`
3. **Make your changes** following the code style guidelines
4. **Add tests** for new functionality
5. **Run the full test suite**: `mvn test`
6. **Commit your changes**: `git commit -am 'Add some feature'`
7. **Push to the branch**: `git push origin feature/my-new-feature`
8. **Submit a pull request** with a clear description

### Pull Request Guidelines

- Provide a clear description of the changes
- Include relevant test cases
- Ensure all tests pass
- Update documentation if necessary
- Reference any related issues

## 📞 Support & Community

### Getting Help

- **📖 Documentation**: [OpenSearch Plugin Documentation](https://opensearch.org/docs/latest/plugins/)
- **🐛 Issues**: [GitHub Issues](https://github.com/codelibs/opensearch-minhash/issues)
- **💬 Discussions**: [GitHub Discussions](https://github.com/codelibs/opensearch-minhash/discussions)

### Reporting Issues

When reporting issues, please include:

- OpenSearch version
- Plugin version  
- Java version
- Operating system
- Complete error messages
- Steps to reproduce
- Sample data/configuration if applicable

### Performance Tips

- Use appropriate `size` values (32-256) based on your accuracy needs
- Consider `bit` values of 1-2 for most use cases
- Store MinHash fields only when necessary
- Use `copy_to` for automatic field population
- Monitor memory usage with large hash sizes

## 📄 License

This project is licensed under the **Apache License 2.0** - see the [LICENSE](LICENSE) file for details.

