package com.xddcodec.fs.file.mapper;

import com.mybatisflex.core.BaseMapper;
import com.xddcodec.fs.file.domain.FileCollection;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface FileCollectionMapper extends BaseMapper<FileCollection> {

    @Update("UPDATE file_collections SET submission_count = submission_count + 1, updated_at = #{updatedAt} WHERE id = #{id}")
    int incrementSubmissionCount(@Param("id") String id, @Param("updatedAt") LocalDateTime updatedAt);

    @Update("UPDATE file_collections SET file_count = file_count + 1, total_size = total_size + #{fileSize}, updated_at = #{updatedAt} WHERE id = #{id}")
    int incrementFileStats(@Param("id") String id,
                           @Param("fileSize") long fileSize,
                           @Param("updatedAt") LocalDateTime updatedAt);
}
