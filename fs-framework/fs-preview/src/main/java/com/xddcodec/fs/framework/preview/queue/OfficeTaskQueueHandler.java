package com.xddcodec.fs.framework.preview.queue;

import com.xddcodec.fs.framework.preview.office.OfficeToPdfConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.concurrent.*;
import java.util.function.BiFunction;

/**
 * Office 任务队列处理器：有界排队 + 单条平台线程串行执行转换。
 * <p>
 * 与 Spring Boot 虚拟线程（请求线程）配合：等待端在虚拟线程上阻塞 {@code CompletableFuture#get} 即可卸载载体；
 * LibreOffice/jodconverter 为进程内重活，必须在平台线程上串行执行，不可用虚拟线程替代消费者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfficeTaskQueueHandler {

    private final OfficeToPdfConfig config;

    private final BlockingQueue<OfficeConvertTask> queue = new LinkedBlockingQueue<>(100);

    private final ExecutorService consumer = Executors.newSingleThreadExecutor(
            Thread.ofPlatform()
                    .name("office-worker")
                    .daemon(true)
                    .factory()
    );

    @PostConstruct
    void startConsumer() {
        consumer.execute(this::consumeLoop);
    }

    private void consumeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                OfficeConvertTask task = queue.take();
                process(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void process(OfficeConvertTask task) {
        try {
            log.info("处理 Office 转 PDF：{}", task.getSourceExtension());
            InputStream result = task.getConverter().apply(task.getSourceStream(), task.getSourceExtension());
            task.getFuture().complete(result);
        } catch (Exception e) {
            task.getFuture().completeExceptionally(e);
        }
    }

    /**
     * 提交任务到队列并等待结果。
     */
    public InputStream submitAndWait(InputStream sourceStream, String ext,
                                     BiFunction<InputStream, String, InputStream> converter) {
        CompletableFuture<InputStream> future = new CompletableFuture<>();
        OfficeConvertTask task = new OfficeConvertTask(sourceStream, ext, future, converter);

        if (!queue.offer(task)) {
            throw new RuntimeException("转换队列已满，服务器繁忙");
        }

        long waitMs = totalWaitMillis();
        try {
            return future.get(waitMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.error("Office 转 PDF 等待超时（{} ms）", waitMs, e);
            throw new RuntimeException("文件预览生成超时或异常");
        } catch (Exception e) {
            log.error("队列任务执行失败", e);
            throw new RuntimeException("文件预览生成超时或异常");
        }
    }

    /**
     * 覆盖：排队等待 + jodconverter 队列/执行超时，避免与底层配置脱节。
     */
    private long totalWaitMillis() {
        long tq = config.getTaskQueueTimeout() != null ? config.getTaskQueueTimeout() : 30_000L;
        long exec = config.getTaskExecutionTimeout() != null ? config.getTaskExecutionTimeout() : 120_000L;
        return tq + exec + 5_000L;
    }

    @PreDestroy
    void shutdown() {
        consumer.shutdownNow();
    }
}
