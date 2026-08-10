package com.focusos.service;

import com.focusos.agent.AgentWorkflowService;
import com.focusos.agent.CareerAgent;
import com.focusos.dto.request.AnalyzeJDRequest;
import com.focusos.dto.request.CreateCareerProfileRequest;
import com.focusos.dto.response.CareerProfileResponse;
import com.focusos.dto.response.JobApplicationResponse;
import com.focusos.entity.CareerAnalysisReport;
import com.focusos.entity.CareerProfile;
import com.focusos.entity.JobApplication;
import com.focusos.exception.BusinessException;
import com.focusos.exception.ResourceNotFoundException;
import com.focusos.repository.CareerAnalysisReportRepository;
import com.focusos.repository.CareerProfileRepository;
import com.focusos.repository.JobApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareerService {

    private final CareerProfileRepository careerProfileRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final CareerAnalysisReportRepository careerReportRepository;
    private final CareerAgent careerAgent;
    private final AgentWorkflowService agentWorkflowService;

    @Transactional
    public CareerProfileResponse createProfile(Long userId, CreateCareerProfileRequest request) {
        careerProfileRepository.findByUserId(userId).ifPresent(existing -> {
            throw new BusinessException("您已有职业档案，请使用更新接口");
        });

        CareerProfile profile = new CareerProfile();
        profile.setUserId(userId);
        profile.setTitle(request.getTitle());
        profile.setSummary(request.getSummary());
        profile.setSkills(request.getSkills());
        profile.setExperience(request.getExperience());
        profile.setEducation(request.getEducation());

        CareerProfile savedProfile = careerProfileRepository.save(profile);
        log.info("Career profile created for user: {}", userId);
        return CareerProfileResponse.fromEntity(savedProfile);
    }

    @Transactional
    public CareerProfileResponse updateProfile(Long userId, CreateCareerProfileRequest request) {
        CareerProfile profile = careerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("职业档案", userId));

        if (request.getTitle() != null) profile.setTitle(request.getTitle());
        if (request.getSummary() != null) profile.setSummary(request.getSummary());
        if (request.getSkills() != null) profile.setSkills(request.getSkills());
        if (request.getExperience() != null) profile.setExperience(request.getExperience());
        if (request.getEducation() != null) profile.setEducation(request.getEducation());

        CareerProfile savedProfile = careerProfileRepository.save(profile);
        log.info("Career profile updated for user: {}", userId);
        return CareerProfileResponse.fromEntity(savedProfile);
    }

    public CareerProfileResponse getProfile(Long userId) {
        CareerProfile profile = careerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("职业档案", userId));
        return CareerProfileResponse.fromEntity(profile);
    }

    public String analyzeJD(Long userId, AnalyzeJDRequest request) {
        CareerProfile profile = careerProfileRepository.findByUserId(userId)
                .orElse(null);

        String skills = profile != null ? profile.getSkills() : "";
        String experience = profile != null ? profile.getExperience() : "";

        // Sprint 5-B: 传入 userId，CareerAgent 会检索用户个人知识库
        String analysis = careerAgent.analyzeJobDescription(
                request.getJobDescription(),
                skills,
                experience,
                userId
        );

        JobApplication app = new JobApplication();
        app.setUserId(userId);
        app.setCompany(request.getCompany());
        app.setPosition(request.getPosition());
        app.setJobDescription(request.getJobDescription());
        app.setStatus("ANALYZED");
        jobApplicationRepository.save(app);

        log.info("JD analyzed for user: {}, position: {} (with Personal RAG)", userId, request.getPosition());
        return analysis;
    }

    /**
     * Sprint 5-B: 基于个人知识库的职业方向推荐
     */
    public String recommendCareerDirections(Long userId) {
        return careerAgent.recommendCareerDirections(userId);
    }

    /**
     * Sprint 5-B: 基于个人知识库的简历优化
     */
    public String optimizeResumeWithProfile(Long userId, String jobDescription) {
        return careerAgent.optimizeResumeWithProfile(jobDescription, userId);
    }

    public String optimizeResume(Long userId, String jobDescription) {
        CareerProfile profile = careerProfileRepository.findByUserId(userId)
                .orElse(null);

        String summary = profile != null ? profile.getSummary() : "";
        String skills = profile != null ? profile.getSkills() : "";
        String experience = profile != null ? profile.getExperience() : "";

        return careerAgent.optimizeResume(
                summary,
                skills,
                experience,
                jobDescription
        );
    }

    public List<JobApplicationResponse> getJobApplications(Long userId) {
        return jobApplicationRepository.findByUserId(userId).stream()
                .map(JobApplicationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<JobApplicationResponse> getJobApplicationsByStatus(Long userId, String status) {
        return jobApplicationRepository.findByUserIdAndStatus(userId, status).stream()
                .map(JobApplicationResponse::fromEntity)
                .collect(Collectors.toList());
    }

    // ===== Sprint 7-A: Career Workflow =====

    /**
     * Sprint 7-A: 启动 Career Workflow（异步 5 步 DAG）
     * 立即返回 workflowId，前端通过 SSE 订阅进度。
     */
    public String startCareerWorkflow(Long userId, String jobDescription, String jobTitle, String company) {
        log.info("Sprint 7-A: startCareerWorkflow for userId={}, jobTitle={}", userId, jobTitle);
        return agentWorkflowService.startCareerWorkflowAsync(userId, jobDescription, jobTitle, company);
    }

    /**
     * 获取用户的全部 Career Analysis 报告（历史查看）
     */
    public List<CareerAnalysisReport> getCareerReports(Long userId) {
        return careerReportRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 获取指定 ID 的 Career 报告
     */
    public CareerAnalysisReport getCareerReport(Long userId, Long reportId) {
        CareerAnalysisReport report = careerReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Career报告", reportId));
        if (!report.getUserId().equals(userId)) {
            throw new BusinessException("无权访问该报告");
        }
        return report;
    }

    /**
     * 通过 workflowId 查询 Career 报告
     */
    public CareerAnalysisReport getCareerReportByWorkflowId(String workflowId) {
        return careerReportRepository.findByWorkflowId(workflowId).orElse(null);
    }
}
