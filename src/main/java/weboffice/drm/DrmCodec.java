package weboffice.drm;

/**
 * 사내 문서보안(DRM) 암복호화.
 *
 * <p>사내에서 오가는 파일은 암호화돼 있다. 올라온 것은 풀어서 읽고, 내려주는 것은
 * 씌워서 준다. 평문 xlsx 가 사내 밖으로 나가지 않게 하는 것이 목적이다.
 *
 * <p>바이트 배열을 주고받는다. 문서 하나가 통째로 메모리에 올라오는 구조라
 * (업로드는 MultipartFile, 내보내기는 POI 가 만든 byte[]) 파일로 떨어뜨릴 이유가
 * 없다. 상한은 spring.servlet.multipart.max-file-size 가 잡는다.
 */
public interface DrmCodec {

    /**
     * 암호화된 것이면 푼다. 평문이면 그대로 돌려준다.
     *
     * <p>평문을 막지 않는 이유: 밖에서 받은 xlsx 는 DRM 이 안 걸려 있다. 그것까지
     * 거절하면 외부 문서를 아예 못 연다.
     *
     * @param fileName 원본 파일명. DRM 헤더에 들어 있는 이름과 맞추는 데 쓴다.
     */
    byte[] decrypt(byte[] data, String fileName);

    /**
     * 암호화한다.
     *
     * @param userId 문서 소유자로 기록할 사번. 열람 권한이 이것으로 정해진다.
     * @throws DrmException 암호화에 실패하면. 평문으로 대신 주지 않는다 -
     *         보호 없는 파일이 나가는 것이 기능이 멈추는 것보다 나쁘다(2026-08-10 결정).
     */
    byte[] encrypt(byte[] data, String fileName, String userId);
}
