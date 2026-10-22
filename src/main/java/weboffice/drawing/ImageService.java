package weboffice.drawing;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.AuditUser;

/**
 * 이미지 바이트 저장·조회.
 *
 * <p>형식은 파일 시그니처(매직 넘버)로 판별한다. 클라이언트가 보낸 Content-Type
 * 이나 확장자는 읽지 않는다 - 둘 다 클라이언트가 정하는 값이라 방어가 되지 않는다.
 * 판별에 실패하면 그것이 곧 거부다. 화이트리스트 대조보다 강하고 코드도 짧다.
 *
 * <p>SVG 가 목록에 없는 것은 빠뜨린 게 아니다. SVG 안의 script 는 우리 오리진에서
 * 실행되므로 받아들이면 업로드가 XSS 통로가 된다.
 */
@Service
public class ImageService {

    /** 시그니처 → MIME. Univer 의 DRAWING_IMAGE_ALLOW_IMAGE_LIST 와 같은 집합이다. */
    private static final List<Signature> SIGNATURES = List.of(
            new Signature(new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
                    "image/png"),
            new Signature(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, "image/jpeg"),
            new Signature(new byte[] {0x47, 0x49, 0x46, 0x38}, "image/gif"),
            new Signature(new byte[] {0x42, 0x4D}, "image/bmp"));

    private final ImageMapper imageMapper;
    private final int maxSize;

    public ImageService(ImageMapper imageMapper,
                        @Value("${weboffice.image.max-size:5242880}") int maxSize) {
        this.imageMapper = imageMapper;
        this.maxSize = maxSize;
    }

    /**
     * 저장하고 IMAGE_SEQ 를 준다. 같은 문서에 같은 바이트가 이미 있으면
     * 새로 넣지 않고 기존 것을 준다.
     *
     * <p>조회 후 삽입 사이에 다른 요청이 같은 바이트를 넣으면 UK 위반이 난다.
     * 막지 않는 이유는 같은 사용자가 같은 그림을 동시에 두 번 올릴 때만 일어나고,
     * 결과가 500 한 번이지 데이터 손상이 아니기 때문이다. 실제로 발생하면 그때
     * 위반을 잡아 재조회하는 처리를 넣는다 - 지금 넣으면 재현할 수 없는 경로를
     * 테스트 없이 두게 된다.
     */
    @Transactional
    public Long store(Long workbookSeq, byte[] bytes, AuditUser user) {
        if (bytes == null || bytes.length == 0) {
            throw new UnsupportedImageException("빈 파일입니다");
        }
        if (bytes.length > maxSize) {
            throw new UnsupportedImageException(
                    "이미지가 너무 큽니다. 최대 " + (maxSize / 1024 / 1024) + "MB 까지 올릴 수 있습니다");
        }

        String mimeType = detectMimeType(bytes);
        String hash = sha256(bytes);

        Long existing = imageMapper.selectImageSeqByHash(workbookSeq, hash);
        if (existing != null) {
            return existing;
        }

        ImageRow image = new ImageRow(null, workbookSeq, hash, mimeType, bytes.length, bytes);
        imageMapper.insertImage(image, user.userKey());
        return image.imageSeq();
    }

    public ImageRow load(Long imageSeq) {
        ImageRow image = imageMapper.selectImage(imageSeq);
        if (image == null) {
            throw new IllegalArgumentException("없는 이미지입니다");
        }
        return image;
    }

    public String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 모든 JRE 가 반드시 제공한다. 여기 오면 JRE 가 깨진 것이다.
            throw new IllegalStateException(e);
        }
    }

    private String detectMimeType(byte[] bytes) {
        for (Signature signature : SIGNATURES) {
            if (signature.matches(bytes)) {
                return signature.mimeType();
            }
        }
        throw new UnsupportedImageException("png, jpeg, gif, bmp 만 올릴 수 있습니다");
    }

    private record Signature(byte[] magic, String mimeType) {

        boolean matches(byte[] bytes) {
            if (bytes.length < magic.length) {
                return false;
            }
            for (int i = 0; i < magic.length; i++) {
                if (bytes[i] != magic[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
