package weboffice.view;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.drm.DrmCodec;
import weboffice.drm.DrmException;

/**
 * 보기 URL 발급.
 *
 * <p>받기 → 복호 → 변환/캐시 → 세션 만들기까지 한 줄기다. 실패하면 어디서 멈췄든
 * 오류 이력을 남긴다 - 부르는 시스템에 코드를 돌려주는 것만으로는 뒤에 무엇이
 * 있었는지 알 수 없다(설계 7절).
 */
@Service
public class ViewIssueService {

    /** 발급 결과. 토큰은 이 응답에만 평문으로 나간다. */
    public record Issued(String token, LocalDateTime expiresAt, String docType,
                         boolean encrypted) {
    }

    private final ViewProperties properties;
    private final SnapshotBuilder snapshotBuilder;
    private final ViewCacheMapper cacheMapper;
    private final ViewSessionMapper sessionMapper;
    private final ViewEventLogger eventLogger;
    private final DrmCodec drmCodec;

    public ViewIssueService(ViewProperties properties, SnapshotBuilder snapshotBuilder,
                            ViewCacheMapper cacheMapper, ViewSessionMapper sessionMapper,
                            ViewEventLogger eventLogger, DrmCodec drmCodec) {
        this.properties = properties;
        this.snapshotBuilder = snapshotBuilder;
        this.cacheMapper = cacheMapper;
        this.sessionMapper = sessionMapper;
        this.eventLogger = eventLogger;
        this.drmCodec = drmCodec;
    }

    @Transactional
    public Issued issue(byte[] bytes, String fileName, String viewerName, String callerSystem,
                        String clientIp) {
        long started = System.currentTimeMillis();
        try {
            long limit = (long) properties.maxFileMb() * 1024 * 1024;
            if (bytes.length > limit) {
                throw new ViewException(ViewErrorCode.TOO_LARGE,
                        "문서가 너무 큽니다: " + properties.maxFileMb() + "MB 넘음");
            }

            byte[] plain = decrypted(bytes, fileName);
            boolean encrypted = plain.length != bytes.length
                    || !java.util.Arrays.equals(plain, bytes);
            String fileHash = sha256(plain);
            LocalDateTime since = LocalDateTime.now().minusMinutes(properties.cacheMinutes());

            ViewCacheRow cached = cacheMapper.selectCache(fileHash, since);
            String docType = cached != null
                    ? cached.docType()
                    : convertAndCache(plain, fileName, encrypted, fileHash);

            String token = ViewToken.make();
            LocalDateTime expiresAt =
                    LocalDateTime.now().plusMinutes(properties.tokenMinutes());
            ViewSessionRow session = new ViewSessionRow(null, ViewToken.hash(token), fileName,
                    docType, fileHash, (long) plain.length, callerSystem, viewerName,
                    encrypted ? "Y" : "N", "ISSUED", expiresAt, null, null, null);
            sessionMapper.insertSession(session, "system");

            eventLogger.log(new ViewEventRow("ISSUE", session.sessionSeq(), callerSystem,
                    fileName, (long) plain.length, encrypted ? "Y" : "N", viewerName,
                    (int) (System.currentTimeMillis() - started), "OK", null, clientIp, null));

            return new Issued(token, expiresAt, docType, encrypted);
        } catch (ViewException e) {
            eventLogger.log(new ViewEventRow("ERROR", null, callerSystem, fileName,
                    (long) bytes.length, null, viewerName,
                    (int) (System.currentTimeMillis() - started), e.code().name(),
                    e.getMessage(), clientIp, null));
            throw e;
        } catch (RuntimeException e) {
            // 코드를 못 붙인 예외를 그대로 흘려보내면 이력도 안 남고 부르는
            // 시스템은 500 만 받는다 - 무엇이 잘못됐는지 알아들을 코드로 바꾼다.
            ViewException wrapped = new ViewException(ViewErrorCode.CONVERT_FAILED,
                    "문서를 처리하지 못했습니다: " + e.getMessage());
            eventLogger.log(new ViewEventRow("ERROR", null, callerSystem, fileName,
                    (long) bytes.length, null, viewerName,
                    (int) (System.currentTimeMillis() - started), wrapped.code().name(),
                    wrapped.getMessage(), clientIp, null));
            throw wrapped;
        }
    }

    /**
     * 변환하고 캐시에 담는다.
     *
     * <p>같은 문서를 동시에 두 요청이 열면 둘 다 캐시가 없다고 보고 둘 다 변환해
     * 담으려 한다. 담기는 upsert 라 FILE_HASH 가 겹쳐도 기본키 충돌이 나지 않고
     * 나중 것이 앞의 것을 덮어쓴다 - 잡을 예외가 없다. 캐시가 식어 selectCache
     * 가 걸러 낸 뒤에도(청소는 10분마다 돈다) 같은 자리에서 upsert 로 새 값이
     * 되므로 기본키 충돌 자체가 생기지 않는다(ViewCache.xml 상단 주석).
     */
    private String convertAndCache(byte[] plain, String fileName, boolean encrypted,
                                   String fileHash) {
        long convertStarted = System.currentTimeMillis();
        ViewSnapshot snapshot = snapshotBuilder.build(plain, fileName, encrypted);
        String docType = snapshot.docType();
        ViewCacheRow row = new ViewCacheRow(fileHash, docType, gzip(snapshot.json()),
                (long) plain.length, (int) (System.currentTimeMillis() - convertStarted),
                encrypted ? "Y" : "N", null);
        cacheMapper.upsertCache(row, "system");
        return docType;
    }

    /** DRM 이 걸렸으면 푼다. 못 풀면 열지 않는다 - 반쯤 열어 주면 화면이 깨진다. */
    private byte[] decrypted(byte[] bytes, String fileName) {
        try {
            return drmCodec.decrypt(bytes, fileName);
        } catch (DrmException e) {
            throw new ViewException(ViewErrorCode.DECRYPT_FAILED,
                    "잠긴 문서를 풀지 못했습니다: " + e.getMessage());
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 이 없는 JVM 은 없다", e);
        }
    }

    private static byte[] gzip(String json) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(json.getBytes(StandardCharsets.UTF_8));
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new ViewException(ViewErrorCode.CONVERT_FAILED, "스냅샷을 담지 못했습니다");
        }
    }
}
