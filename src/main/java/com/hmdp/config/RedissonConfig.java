package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient()
    {
        //创建Redisson的配置类对象
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379");
        //创建RedissonClient对象但会
        return Redisson.create(config);
    }
}
