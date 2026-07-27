package com.xddcodec.fs.file.mapper;

import com.mybatisflex.core.BaseMapper;
import com.xddcodec.fs.file.domain.FileCollectionSubmission;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface FileCollectionSubmissionMapper extends BaseMapper<FileCollectionSubmission> {

    @Update("UPDATE file_collection_submissions SET file_count = file_count + 1, total_size = total_size + #{fileSize}, updated_at = #{updatedAt} WHERE id = #{id}")
    int incrementFileStats(@Param("id") String id,
                           @Param("fileSize") long fileSize,
                           @Param("updatedAt") LocalDateTime updatedAt);
}
