package com.flowpilot.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowpilot.config.FlowPilotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * 截图 OCR 服务（可插拔）：
 *  - disabled：不识别，截图按图片消息归档并提示人工补录（默认，零依赖）；
 *  - baidu：百度智能云通用文字识别（需开通并配置 API Key / Secret Key）。
 * 扩展其它 OCR（如本地 PaddleOCR）时实现本类同型接口即可。
 */
@Component
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FlowPilotProperties props;
    private final RestClient client;

    public OcrService(FlowPilotProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean enabled() {
        return "baidu".equalsIgnoreCase(props.getWechat().getOcr().getProvider());
    }

    /**
     * 识别图片文字。
     * @return 识别文本；未启用时返回空串
     */
    public String recognize(byte[] imageBytes) {
        if (!enabled()) {
            return "";
        }
        try {
            String token = baiduToken();
            String base64 = URLEncoder.encode(Base64.getEncoder().encodeToString(imageBytes),
                    StandardCharsets.UTF_8);
            JsonNode resp = client.post()
                    .uri("https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic?access_token=" + token)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body("image=" + base64 + "&language_type=CHN_ENG")
                    .retrieve().body(JsonNode.class);
            if (resp == null) {
                return "";
            }
            if (resp.has("error_code")) {
                log.warn("百度 OCR 失败: {}", resp);
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode w : resp.path("words_result")) {
                sb.append(w.path("words").asText()).append('\n');
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("百度 OCR 调用异常: {}", e.getMessage());
            return "";
        }
    }

    private String baiduToken() {
        FlowPilotProperties.Ocr ocr = props.getWechat().getOcr();
        JsonNode resp = client.post()
                .uri("https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id="
                        + ocr.getBaiduApiKey() + "&client_secret=" + ocr.getBaiduSecretKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("")
                .retrieve().body(JsonNode.class);
        if (resp == null || !resp.has("access_token")) {
            throw new IllegalStateException("百度 OCR token 获取失败: " + resp);
        }
        return resp.path("access_token").asText();
    }
}
