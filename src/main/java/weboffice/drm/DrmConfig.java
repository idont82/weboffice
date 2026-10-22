package weboffice.drm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DrmConfig {

    /**
     * 문서보안 암복호화기.
     *
     * <p>기본은 아무것도 하지 않는 구현이다. 상용 문서보안 제품을 가진 사람은
     * 이 빈만 자기 구현으로 바꿔 끼운다.
     *
     * <p>끄는 스위치를 두지 않는다. 켜고 끌 수 있으면 언젠가 꺼진 채로 운영에
     * 올라가고, 그때 나가는 것은 보호 없는 문서다. 끼울 것이 없으면 처음부터
     * 없는 것이 낫다 - 꺼진 것과 없는 것은 읽는 사람에게 다른 말이다.
     */
    @Bean
    public DrmCodec drmCodec() {
        return new PlainDrmCodec();
    }
}
