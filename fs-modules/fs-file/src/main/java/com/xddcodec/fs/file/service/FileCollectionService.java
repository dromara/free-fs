package com.xddcodec.fs.file.service;

import com.mybatisflex.core.service.IService;
import com.xddcodec.fs.file.domain.FileCollection;
import com.xddcodec.fs.file.domain.dto.CreateFileCollectionCmd;
import com.xddcodec.fs.file.domain.dto.CreateFileCollectionSubmissionCmd;
import com.xddcodec.fs.file.domain.qry.FileCollectionQry;
import com.xddcodec.fs.file.domain.qry.FileCollectionSubmissionQry;
import com.xddcodec.fs.file.domain.vo.FileCollectionPublicVO;
import com.xddcodec.fs.file.domain.vo.FileCollectionSubmissionSessionVO;
import com.xddcodec.fs.file.domain.vo.FileCollectionSubmissionVO;
import com.xddcodec.fs.file.domain.vo.FileCollectionUploadContext;
import com.xddcodec.fs.file.domain.vo.FileCollectionDeletionResult;
import com.xddcodec.fs.file.domain.vo.FileCollectionVO;
import com.xddcodec.fs.file.enums.FileCollectionStatus;
import com.xddcodec.fs.framework.common.domain.PageResult;

public interface FileCollectionService extends IService<FileCollection> {
    PageResult<FileCollectionVO> getPages(FileCollectionQry qry);
    FileCollectionVO getDetail(String collectionId);
    FileCollectionVO createCollection(CreateFileCollectionCmd cmd);
    FileCollectionVO updateStatus(String collectionId, FileCollectionStatus status);
    FileCollectionDeletionResult deleteCollection(String collectionId);
    PageResult<FileCollectionSubmissionVO> getSubmissions(String collectionId, FileCollectionSubmissionQry qry);
    FileCollectionPublicVO getPublicInfo(String collectionId);
    FileCollectionSubmissionSessionVO startSubmission(String collectionId, CreateFileCollectionSubmissionCmd cmd);
    void completeSubmission(String collectionId, String submissionId, String uploadToken);
    FileCollectionUploadContext authorizeUpload(String collectionId, String submissionId, String uploadToken);
    void recordUploadedFile(String collectionId, String submissionId, long fileSize);
}
