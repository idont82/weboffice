package weboffice.sheet;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.Chunks;
import weboffice.common.AfterCommit;
import weboffice.common.AuditUser;
import weboffice.realtime.RealtimeHub;
import weboffice.security.Actor;
import weboffice.security.UserContext;
import weboffice.sheet.SheetSizeDto.AxisType;

/**
 * 행 높이·열 너비.
 *
 * <p>손댄 줄만 남긴다. 시트 하나에 행이 백만 개일 수 있어 전부 적으면 표가 터진다 -
 * 기본 크기로 되돌린 줄은 지우고 화면 기본값이 채우게 둔다.
 */
@Service
public class SheetSizeService {

    private final SheetSizeMapper sheetSizeMapper;
    private final RealtimeHub realtimeHub;

    public SheetSizeService(SheetSizeMapper sheetSizeMapper, RealtimeHub realtimeHub) {
        this.sheetSizeMapper = sheetSizeMapper;
        this.realtimeHub = realtimeHub;
    }

    public List<SheetSizeDto> read(Long sheetSeq) {
        return sheetSizeMapper.selectSizes(sheetSeq);
    }

    @Transactional
    public int saveDelta(Long sheetSeq, List<SheetSizeDto> changed, List<SheetSizeDto> deleted,
                         AuditUser user) {
        validate(changed, true);
        validate(deleted, false);

        // 한 문장에 다 밀어 넣으면 파라미터 상한을 넘어 통째로 실패한다(2026-08-18
        // 지적: 큰 xlsx 를 올리다 파라미터 249,326개).
        for (List<SheetSizeDto> chunk : Chunks.of(changed)) {
            sheetSizeMapper.upsertSizes(sheetSeq, chunk, user.userKey());
        }
        for (List<SheetSizeDto> chunk : Chunks.of(deleted)) {
            sheetSizeMapper.softDeleteSizes(sheetSeq, chunk, user.userKey());
        }

        Actor actor = UserContext.currentActor();
        AfterCommit.run(() -> realtimeHub.sizesChanged(sheetSeq, actor, changed, deleted));
        return changed.size() + deleted.size();
    }

    /**
     * 축 이름과 크기를 확인한다.
     *
     * <p>0 은 "숨김" 이다. 표에 칸을 더하지 않으려고 크기 자리를 그 뜻으로 쓴다 -
     * 엑셀도 너비 0 을 숨김으로 본다. 숨김을 담을 자리가 없던 때에는 열을 숨겨도
     * 새로고침하면 되살아났다(2026-09-03 지적).
     *
     * <p>음수는 막는다. 그런 크기는 화면이 그릴 수 없고, 들어오면 어느 줄이
     * 사라졌는지 찾기 어렵다.
     */
    private void validate(List<SheetSizeDto> sizes, boolean needsSize) {
        for (SheetSizeDto size : sizes) {
            AxisType.parse(size.axisType());
            if (needsSize && (size.sizePx() == null || size.sizePx() < 0)) {
                throw new IllegalArgumentException("크기는 0(숨김) 이상이어야 합니다");
            }
        }
    }
}
