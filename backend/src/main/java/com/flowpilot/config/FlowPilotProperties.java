package com.flowpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * FlowPilot 全部业务配置，对应 application.yml 中 flowpilot.* 前缀，
 * 支持用 FLOWPILOT_ 开头的环境变量覆盖（Spring 标准 relaxed binding）。
 */
@ConfigurationProperties(prefix = "flowpilot")
public class FlowPilotProperties {

    /** AI 大模型配置 */
    private final Ai ai = new Ai();

    /** 认证配置 */
    private final Auth auth = new Auth();

    /** 飞书开放平台配置 */
    private final Feishu feishu = new Feishu();

    /** 企业微信配置 */
    private final WeCom wecom = new WeCom();

    /** 微信个人版导入配置 */
    private final WeChat wechat = new WeChat();

    /** 邮件接入配置（IMAP） */
    private final Email email = new Email();

    /** 通知与定时任务配置 */
    private final Notify notify = new Notify();

    /** 数据治理配置 */
    private final Data data = new Data();

    /** 是否初始化演示数据 */
    private boolean seedDemo = true;

    public Ai getAi() { return ai; }
    public Auth getAuth() { return auth; }
    public Feishu getFeishu() { return feishu; }
    public WeCom getWecom() { return wecom; }
    public WeChat getWechat() { return wechat; }
    public Email getEmail() { return email; }
    public Notify getNotify() { return notify; }
    public Data getData() { return data; }
    public boolean isSeedDemo() { return seedDemo; }
    public void setSeedDemo(boolean seedDemo) { this.seedDemo = seedDemo; }

    public static class Ai {
        /** mock / anthropic / openai */
        private String provider = "mock";
        /** 分析并发线程数（PRD 要求支持 >=50） */
        private int parallelism = 8;
        /** LLM 调用超时（秒） */
        private int timeoutSeconds = 120;
        /** 单次分析携带的最大消息条数 */
        private int maxMessagesPerAnalysis = 200;
        private final Anthropic anthropic = new Anthropic();
        private final OpenAi openai = new OpenAi();

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public int getParallelism() { return parallelism; }
        public void setParallelism(int parallelism) { this.parallelism = parallelism; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxMessagesPerAnalysis() { return maxMessagesPerAnalysis; }
        public void setMaxMessagesPerAnalysis(int maxMessagesPerAnalysis) { this.maxMessagesPerAnalysis = maxMessagesPerAnalysis; }
        public Anthropic getAnthropic() { return anthropic; }
        public OpenAi getOpenai() { return openai; }
    }

    public static class Anthropic {
        private String apiKey = "";
        private String model = "claude-opus-5";
        private String baseUrl = "";
        /**
         * 严格结构化输出：官方 Anthropic API 支持服务端 Schema 校验时保持 true；
         * 第三方 Anthropic 兼容网关（中转站）通常忽略 Schema，需设为 false，
         * 改用「提示词约束 + 本地 JSON 修复」的宽松模式。
         */
        private boolean strictSchema = true;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public boolean isStrictSchema() { return strictSchema; }
        public void setStrictSchema(boolean strictSchema) { this.strictSchema = strictSchema; }
    }

    public static class OpenAi {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-4o-mini";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class Auth {
        /** JWT 签名密钥，生产环境务必通过环境变量覆盖 */
        private String jwtSecret = "flowpilot-dev-secret-change-me-in-production";
        /** Token 有效期（小时） */
        private int tokenExpireHours = 24;

        public String getJwtSecret() { return jwtSecret; }
        public void setJwtSecret(String jwtSecret) { this.jwtSecret = jwtSecret; }
        public int getTokenExpireHours() { return tokenExpireHours; }
        public void setTokenExpireHours(int tokenExpireHours) { this.tokenExpireHours = tokenExpireHours; }
    }

    public static class Feishu {
        private String appId = "";
        private String appSecret = "";
        /** 事件订阅加密 Key（开启加密时必填） */
        private String encryptKey = "";
        /** 事件订阅验证 Token */
        private String verificationToken = "";

        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
        public String getEncryptKey() { return encryptKey; }
        public void setEncryptKey(String encryptKey) { this.encryptKey = encryptKey; }
        public String getVerificationToken() { return verificationToken; }
        public void setVerificationToken(String verificationToken) { this.verificationToken = verificationToken; }
    }

    public static class WeCom {
        private String corpId = "";
        private String corpSecret = "";
        private String agentId = "";
        /** 回调消息加密 Key（Base64 编码） */
        private String encodingAesKey = "";
        private String token = "";

        public String getCorpId() { return corpId; }
        public void setCorpId(String corpId) { this.corpId = corpId; }
        public String getCorpSecret() { return corpSecret; }
        public void setCorpSecret(String corpSecret) { this.corpSecret = corpSecret; }
        public String getAgentId() { return agentId; }
        public void setAgentId(String agentId) { this.agentId = agentId; }
        public String getEncodingAesKey() { return encodingAesKey; }
        public void setEncodingAesKey(String encodingAesKey) { this.encodingAesKey = encodingAesKey; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }

    public static class WeChat {
        /** 导入文件夹监控目录（自动导入） */
        private String watchDir = "./data/watch";
        /** 是否启用文件夹监控 */
        private boolean watchEnabled = true;
        /** OCR 配置 */
        private final Ocr ocr = new Ocr();
        /** 可参与自动归属的聊天导出文件名关键词（默认取项目名称匹配） */
        private final List<String> projectMatchHints = new ArrayList<>();

        public String getWatchDir() { return watchDir; }
        public void setWatchDir(String watchDir) { this.watchDir = watchDir; }
        public boolean isWatchEnabled() { return watchEnabled; }
        public void setWatchEnabled(boolean watchEnabled) { this.watchEnabled = watchEnabled; }
        public Ocr getOcr() { return ocr; }
        public List<String> getProjectMatchHints() { return projectMatchHints; }
    }

    public static class Ocr {
        /** disabled / baidu */
        private String provider = "disabled";
        private String baiduApiKey = "";
        private String baiduSecretKey = "";

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getBaiduApiKey() { return baiduApiKey; }
        public void setBaiduApiKey(String apiKey) { this.baiduApiKey = apiKey; }
        public String getBaiduSecretKey() { return baiduSecretKey; }
        public void setBaiduSecretKey(String secretKey) { this.baiduSecretKey = secretKey; }
    }

    /**
     * 邮件接入配置。注意：IMAP 密码建议使用「邮箱授权码/应用专用密码」，
     * 切勿直接使用主密码；密码仅通过环境变量注入，不落代码仓库。
     */
    public static class Email {
        private boolean enabled = false;
        private String imapHost = "";
        private int imapPort = 993;
        private String username = "";
        private String password = "";
        /** 拉取的邮件文件夹 */
        private String folder = "INBOX";
        /** 轮询间隔（分钟） */
        private int pollMinutes = 15;
        /** 每次轮询最多处理的邮件数 */
        private int maxPerPoll = 50;
        /** 只拉取最近 N 天内的邮件 */
        private int lookbackDays = 14;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getImapHost() { return imapHost; }
        public void setImapHost(String imapHost) { this.imapHost = imapHost; }
        public int getImapPort() { return imapPort; }
        public void setImapPort(int imapPort) { this.imapPort = imapPort; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getFolder() { return folder; }
        public void setFolder(String folder) { this.folder = folder; }
        public int getPollMinutes() { return pollMinutes; }
        public void setPollMinutes(int pollMinutes) { this.pollMinutes = pollMinutes; }
        public int getMaxPerPoll() { return maxPerPoll; }
        public void setMaxPerPoll(int maxPerPoll) { this.maxPerPoll = maxPerPoll; }
        public int getLookbackDays() { return lookbackDays; }
        public void setLookbackDays(int lookbackDays) { this.lookbackDays = lookbackDays; }
    }

    public static class Notify {
        /** 每日摘要推送 cron */
        private String digestCron = "0 0 9 * * ?";
        /** 渠道增量同步 cron（默认每 30 分钟） */
        private String syncCron = "0 */30 * * * ?";
        /** SLA 巡检 cron（默认每小时） */
        private String slaCheckCron = "0 5 * * * ?";
        /** 周报生成 cron（周一 8 点） */
        private String weeklyReportCron = "0 0 8 ? * MON";
        /** 月报生成 cron（每月 1 日 8 点） */
        private String monthlyReportCron = "0 0 8 1 * ?";
        /** 飞书群机器人 webhook（通知推送目标，可空） */
        private String feishuWebhook = "";
        /** 企微群机器人 webhook（通知推送目标，可空） */
        private String wecomWebhook = "";

        public String getDigestCron() { return digestCron; }
        public void setDigestCron(String digestCron) { this.digestCron = digestCron; }
        public String getSyncCron() { return syncCron; }
        public void setSyncCron(String syncCron) { this.syncCron = syncCron; }
        public String getSlaCheckCron() { return slaCheckCron; }
        public void setSlaCheckCron(String slaCheckCron) { this.slaCheckCron = slaCheckCron; }
        public String getWeeklyReportCron() { return weeklyReportCron; }
        public void setWeeklyReportCron(String weeklyReportCron) { this.weeklyReportCron = weeklyReportCron; }
        public String getMonthlyReportCron() { return monthlyReportCron; }
        public void setMonthlyReportCron(String monthlyReportCron) { this.monthlyReportCron = monthlyReportCron; }
        public String getFeishuWebhook() { return feishuWebhook; }
        public void setFeishuWebhook(String feishuWebhook) { this.feishuWebhook = feishuWebhook; }
        public String getWecomWebhook() { return wecomWebhook; }
        public void setWecomWebhook(String wecomWebhook) { this.wecomWebhook = wecomWebhook; }
    }

    public static class Data {
        /** 原始聊天记录留存天数，到期自动清理（默认 90 天，PRD 7.2） */
        private int retentionDays = 90;

        public int getRetentionDays() { return retentionDays; }
        public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
    }
}
