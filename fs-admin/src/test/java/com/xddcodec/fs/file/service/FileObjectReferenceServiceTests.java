package com.xddcodec.fs.file.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.xddcodec.fs.file.domain.FileInfo;
import com.xddcodec.fs.framework.redis.repository.RedisRepository;
import com.xddcodec.fs.storage.facade.StorageServiceFacade;
import com.xddcodec.fs.storage.plugin.core.IStorageOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileObjectReferenceServiceTests {

    @Mock
    private FileInfoService fileInfoService;

    @Mock
    private StorageServiceFacade storageServiceFacade;

    @Mock
    private RedisRepository redisRepository;

    @Mock
    private IStorageOperationService storageService;

    private FileObjectReferenceService referenceService;

    @BeforeEach
    void setUp() {
        referenceService = new FileObjectReferenceService(
                fileInfoService,
                storageServiceFacade,
                redisRepository
        );
    }

    @Test
    void shouldReuseObjectReferencedOnlyByRecycleBin() {
        FileInfo recycleFile = file("file-1", "setting-1", "object-key");
        recycleFile.setIsDeleted(true);

        when(fileInfoService.list(any(QueryWrapper.class))).thenReturn(List.of(recycleFile));
        when(storageServiceFacade.getStorageService("setting-1")).thenReturn(storageService);
        when(storageService.isFileExist("object-key")).thenReturn(true);

        FileInfo result = referenceService.findReusableFile("md5", 1024L, "setting-1");

        assertSame(recycleFile, result);
    }

    @Test
    void shouldKeepPhysicalObjectWhileAnyReferenceExists() {
        FileInfo file = file("file-1", "setting-1", "object-key");
        when(fileInfoService.count(any(QueryWrapper.class))).thenReturn(1L);

        referenceService.deletePhysicalFileIfUnreferenced(file);

        verify(storageServiceFacade, never()).getStorageService(any());
    }

    @Test
    void shouldDeletePhysicalObjectAfterLastReferenceIsRemoved() {
        FileInfo file = file("file-1", "setting-1", "object-key");
        when(fileInfoService.count(any(QueryWrapper.class))).thenReturn(0L);
        when(storageServiceFacade.getStorageService("setting-1")).thenReturn(storageService);
        when(redisRepository.setIfAbsent(anyString(), anyString(), anyLong())).thenReturn(true);
        when(redisRepository.compareAndDelete(anyString(), anyString())).thenReturn(true);

        referenceService.deletePhysicalFileIfUnreferencedWithLock(file);

        verify(storageService).deleteFile("object-key");
        verify(redisRepository, org.mockito.Mockito.times(2))
                .compareAndDelete(anyString(), anyString());
    }

    private FileInfo file(String id, String storageSettingId, String objectKey) {
        FileInfo file = new FileInfo();
        file.setId(id);
        file.setStoragePlatformSettingId(storageSettingId);
        file.setObjectKey(objectKey);
        file.setContentMd5("md5");
        file.setSize(1024L);
        file.setIsDir(false);
        return file;
    }
}
