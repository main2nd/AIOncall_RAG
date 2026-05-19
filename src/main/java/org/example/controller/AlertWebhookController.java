package org.example.controller;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.example.service.AiOpsService;
import org.example.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 告警 Webhook 控制器
 * 接收来自 Prometheus/Alertmanager 的实时告警推送并触发 AI 分析
 */
@RestController
@RequestMapping("/api/webhook")
public class AlertWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(AlertWebhookController.class);

    @Autowired
    private AiOpsService aiOpsService;

    @Autowired
    private ChatService chatService;

    /**
     * 接收 Prometheus 告警推送
     * 
     * @param alertData 告警数据（JSON 格式）
     * @return 响应结果
     */
    @PostMapping("/prometheus")
    public ResponseEntity<Map<String, String>> handleAlert(@RequestBody Map<String, Object> alertData) {
        logger.info("🚨 [Webhook] 收到实时告警推送！正在异步启动 AI 诊断流程...");
        
        // 记录收到的部分告警信息，方便排查
        logger.debug("告警详细内容: {}", alertData);

        // 异步执行 AI 分析，避免阻塞 Webhook 响应
        CompletableFuture.runAsync(() -> {
            try {
                logger.info("🤖 [AI 诊断] 启动多 Agent 协作分析...");
                
                // 1. 创建模型和获取工具
                DashScopeChatModel chatModel = chatService.createStandardChatModel(chatService.createDashScopeApi());
                ToolCallback[] toolCallbacks = chatService.getToolCallbacks();

                // 2. 执行 AI Ops 分析流程
                Optional<OverAllState> stateOptional = aiOpsService.executeAiOpsAnalysis(chatModel, toolCallbacks);

                // 3. 提取并输出结果
                if (stateOptional.isPresent()) {
                    Optional<String> reportOptional = aiOpsService.extractFinalReport(stateOptional.get());
                    if (reportOptional.isPresent()) {
                        logger.info("✅ [AI 诊断完成] 自动生成的分析报告如下：\n\n{}\n", reportOptional.get());
                    } else {
                        logger.warn("⚠️ [AI 诊断完成] 流程执行完毕，但未能生成有效的 Markdown 报告。");
                    }
                }
            } catch (Exception e) {
                logger.error("❌ [AI 诊断失败] 自动化分析流程出现异常", e);
            }
        });

        // 立即返回给 Alertmanager，表示已经接收
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Alert received, AI diagnosis started asynchronously"
        ));
    }
}
