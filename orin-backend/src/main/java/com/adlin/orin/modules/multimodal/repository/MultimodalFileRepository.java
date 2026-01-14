package com.adlin.orin.modules.multimodal.repository;

import com.adlin.orin.modules.multimodal.entity.MultimodalFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MultimodalFileRepository extends JpaRepository<MultimodalFile, String> {

    /**
     * 按文件类型查询
     */
    List<MultimodalFile> findByFileTypeOrderByUploadedAtDesc(String fileType);

    /**
     * 按上传者查询
     */
    List<MultimodalFile> findByUploadedByOrderByUploadedAtDesc(String uploadedBy);

    /**
     * 统计文件类型数量
     */
    long countByFileType(String fileType);
}
