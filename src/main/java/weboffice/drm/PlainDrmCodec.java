package weboffice.drm;

/**
 * 아무것도 하지 않는 암복호화기. 공개판의 기본 구현이다.
 *
 * <p>문서보안 제품은 보통 재배포할 수 없는 jar 로 온다. 그래서 기본 구현은
 * 아무것도 끼우지 않은 상태다 - 들여온 바이트를 그대로 읽고, 내보낼 바이트를
 * 그대로 준다.
 *
 * <p><b>암호화된 파일을 알아보지 못한다.</b> 판정 자체가 걷어낸 제품이 하는
 * 일이다. DRM 걸린 파일을 넣으면 깨진 파일로 보여 읽기가 실패한다. 형식을
 * 직접 뜯어(OLE/ZIP 서명) 판정하는 것은 이 자리의 일이 아니다 - 그것은 새
 * 기능이고, 반쪽만 맞는 판정은 없는 것보다 나쁘다.
 *
 * <p>{@link DrmCodec} 을 인터페이스로 남겨 둔 이유: 자기 문서보안 제품을 가진
 * 사람이 이 구현만 바꿔 끼울 수 있게 하기 위한 것이다.
 */
public class PlainDrmCodec implements DrmCodec {

    @Override
    public byte[] decrypt(byte[] data, String fileName) {
        return data.clone();
    }

    @Override
    public byte[] encrypt(byte[] data, String fileName, String userId) {
        return data.clone();
    }
}
