package com.focusos.dto.response;

import com.focusos.entity.KnowledgeDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentResponse {

    private Long id;
    private String title;
    private String fileType;
    private String filePath;
    private Long fileSize;
    private String category;
    private String documentType;
    private Integer priority;
    private String source;
    private String tags;
    private Boolean isVectorized;
    private LocalDateTime createdAt;

    public static KnowledgeDocumentResponse fromEntity(KnowledgeDocument doc) {
        return new KnowledgeDocumentResponse(
            doc.getId(),
            doc.getTitle(),
            doc.getFileType(),
            doc.getFilePath(),
            doc.getFileSize(),
            doc.getCategory(),
            doc.getDocumentType(),
            doc.getPriority(),
            doc.getSource(),
            doc.getTags(),
            doc.getIsVectorized(),
            doc.getCreatedAt()
        );
    }
}
