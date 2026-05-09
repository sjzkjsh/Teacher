package com.example.aiteacher.Config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

/**
 * 手动定义 VectorStore Bean
 * 不依赖 Spring AI 的 auto-configuration
 */
@Configuration
public class VectorStoreConfig {
    @Bean
    public JedisPooled jedisPooled() {
        // 根据 application.yml 中的配置：host, port, password
        return new JedisPooled("localhost", 6379, "default", "infini_rag_flow");
    }

    @Bean
    public VectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName("my-ai-index")     // 索引名称，与 yml 中保持一致
                .prefix("doc:")               // key 前缀
                .initializeSchema(true)       // 自动创建索引结构
                .build();
    }

}
