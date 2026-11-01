package com.campuslink.module.forum.infrastructure.search;

import com.campuslink.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * MeiliSearch HTTP 客户端（CR-181）：独立 bean，避免与其它 RestClient 共用 baseUrl / 凭据。
 *
 * <p>超时写死为 2s 连接 / 5s 读取：搜索是同步请求，Meili 不可用时应尽快失败并由应用层回落 MySQL，
 * 而不是把 Tomcat 线程一起拖住。索引写操作只等待 task 回执，不在这里等任务完成。
 */
@Configuration
public class MeiliSearchConfig {

    @Bean
    public RestClient meiliRestClient(AppProperties props) {
        AppProperties.Search.Meili meili = props.getSearch().getMeili();
        // 必须用 JDK HttpClient：SimpleClientHttpRequestFactory 底层的 HttpURLConnection 不支持 PATCH，
        // Meili 的 settings 端点只接受 PATCH（真机首轮 500 的根因）。
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(meili.getUrl())
                .requestFactory(factory);
        if (StringUtils.hasText(meili.getApiKey())) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + meili.getApiKey());
        }
        return builder.build();
    }
}
