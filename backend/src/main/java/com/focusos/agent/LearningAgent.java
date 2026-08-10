package com.focusos.agent;

import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LearningAgent implements FocusAgent {

    private final ChatLanguageModel chatLanguageModel;
    private final AgentPromptProvider promptProvider;

    public String generateLearningPlan(String goal, int durationWeeks, int dailyMinutes) {
        String prompt = String.format("""
                你是一位专业的学习规划师。请根据以下信息为用户生成一个详细的学习计划：

                学习目标：%s
                学习周期：%d周
                每日目标：%d分钟

                请输出：
                1. 每周的学习主题和重点
                2. 每日的学习任务安排
                3. 推荐的学习资源类型
                4. 进度里程碑和检查点
                5. 保持学习动力的建议

                请用中文回答，格式清晰、可执行。
                """, goal, durationWeeks, dailyMinutes);

        String fullPrompt = promptProvider.learningSystemPrompt() + "\n\n" + prompt;

        try {
            return chatLanguageModel.chat(fullPrompt);
        } catch (Exception e) {
            log.error("Failed to generate learning plan", e);
            return "学习计划生成失败，请稍后重试。";
        }
    }

    public String dailyReview(String dailySummary) {
        String prompt = String.format("""
                你是一位学习助手。请根据用户今日的学习情况进行复盘和总结：

                %s

                请提供：
                1. 今日学习成果总结
                2. 学习效率评估
                3. 改进建议
                4. 明日学习重点建议

                请用中文回答，语气友好、鼓励性强。
                """, dailySummary);

        String fullPrompt = promptProvider.learningSystemPrompt() + "\n\n" + prompt;

        try {
            return chatLanguageModel.chat(fullPrompt);
        } catch (Exception e) {
            log.error("Failed to generate daily review", e);
            return "每日复盘生成失败，请稍后重试。";
        }
    }

    public String recommendStudyMethod(String subject, int availableMinutes) {
        String prompt = String.format("""
                你是一位学习方法专家。请为以下场景推荐合适的学习方法：

                学习科目：%s
                可用时间：%d分钟

                请推荐：
                1. 最适合的学习方法（如番茄工作法、间隔重复等）
                2. 具体的执行步骤
                3. 注意力保持技巧
                4. 高效学习的小贴士
                """, subject, availableMinutes);

        String fullPrompt = promptProvider.learningSystemPrompt() + "\n\n" + prompt;

        try {
            return chatLanguageModel.chat(fullPrompt);
        } catch (Exception e) {
            log.error("Failed to recommend study method", e);
            return "学习方法推荐失败，请稍后重试。";
        }
    }

    @Override
    public String type() {
        return "learning";
    }

    @Override
    public String handle(String message, Long userId, String context) {
        String prompt = String.format("""
                你是一位专业的学习规划师和辅导助手。

                对话上下文：
                %s

                用户问题：
                %s

                请根据上下文和用户问题，提供学习建议、计划规划或学习方法指导。
                请用中文回答，语气友好、专业、具有指导性。
                """, context != null ? context : "无", message);

        String fullPrompt = promptProvider.learningSystemPrompt() + "\n\n" + prompt;

        try {
            return chatLanguageModel.chat(fullPrompt);
        } catch (Exception e) {
            log.error("Failed to handle learning agent request", e);
            return "学习助手暂时无法回答，请稍后重试。";
        }
    }
}