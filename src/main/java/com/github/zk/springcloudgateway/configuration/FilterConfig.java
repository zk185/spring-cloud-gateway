package com.github.zk.springcloudgateway.configuration;

import com.github.zk.springcloudgateway.jwt.TokenCheckGatewayFilterFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author zk
 * @date 2019/6/5 9:56
 */
@Configuration
public class FilterConfig {

    @Bean
    public TokenCheckGatewayFilterFactory getJwtCheck() {
        return new TokenCheckGatewayFilterFactory();
    }
}
