package weboffice.view;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * 다른 시스템 URL 에서 문서를 받아 온다.
 *
 * <p>크기를 <b>읽으면서</b> 잰다. Content-Length 만 믿으면 그것을 안 주는 서버에서
 * 한도가 없는 것과 같다.
 */
@Service
public class DocumentFetcher {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public byte[] fetch(String fileUrl, Map<String, String> headers, int maxFileMb) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(fileUrl))
                .timeout(TIMEOUT)
                .GET();
        if (headers != null) {
            headers.forEach(request::header);
        }
        try {
            HttpResponse<InputStream> response =
                    client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() / 100 != 2) {
                throw new ViewException(ViewErrorCode.FETCH_FAILED,
                        "문서를 받아 오지 못했습니다: HTTP " + response.statusCode());
            }
            return readAtMost(response.body(), maxFileMb);
        } catch (IOException e) {
            throw new ViewException(ViewErrorCode.FETCH_FAILED,
                    "문서를 받아 오지 못했습니다: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ViewException(ViewErrorCode.FETCH_FAILED, "문서를 받다가 끊겼습니다");
        }
    }

    private static byte[] readAtMost(InputStream in, int maxFileMb) throws IOException {
        long limit = (long) maxFileMb * 1024 * 1024;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new ViewException(ViewErrorCode.TOO_LARGE,
                        "문서가 너무 큽니다: " + maxFileMb + "MB 넘음");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
