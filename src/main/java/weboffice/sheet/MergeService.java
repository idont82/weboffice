package weboffice.sheet;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.Chunks;
import weboffice.common.AuditUser;
import weboffice.excel.MergeRangeDto;

/**
 * 병합 범위 읽기·쓰기.
 *
 * <p>델타가 아니라 통째로 갈아치운다. 병합은 시트당 많아야 수백 개고, 하나를 풀면
 * 그 자리에 다른 병합이 생기는 식이라 "바뀐 것" 을 가려내는 쪽이 오히려 어긋나기
 * 쉽다. 셀·크기와 다른 이유가 이것이다 - 그쪽은 백만 줄이 될 수 있어 델타가 아니면
 * 안 된다.
 */
@Service
public class MergeService {

    private final MergeMapper mergeMapper;

    public MergeService(MergeMapper mergeMapper) {
        this.mergeMapper = mergeMapper;
    }

    public List<MergeRangeDto> read(Long sheetSeq) {
        return mergeMapper.selectMerges(sheetSeq);
    }

    @Transactional
    public int replaceAll(Long sheetSeq, List<MergeRangeDto> merges, AuditUser user) {
        merges.forEach(MergeService::validate);

        // 지우고 넣는다. 소프트 삭제라 같은 범위를 다시 넣어도 옛 행이 남는데,
        // 조회가 DEL_DATETIME IS NULL 만 보므로 중복으로 나오지 않는다.
        mergeMapper.deleteAllMerges(sheetSeq, user.userKey());
        if (merges.isEmpty()) {
            return 0;
        }
        for (List<MergeRangeDto> chunk : Chunks.of(merges)) {
            mergeMapper.insertMerges(sheetSeq, chunk, user.userKey());
        }
        return merges.size();
    }

    private static void validate(MergeRangeDto merge) {
        if (merge.toRowNo() < merge.fromRowNo() || merge.toColumnNo() < merge.fromColumnNo()) {
            // 뒤집힌 범위를 그대로 두면 그리는 쪽이 음수 크기를 받는다.
            throw new IllegalArgumentException("병합 범위가 뒤집혔습니다");
        }
        if (merge.fromRowNo() == merge.toRowNo() && merge.fromColumnNo() == merge.toColumnNo()) {
            // 한 칸은 병합이 아니다. 남겨 두면 푼 자리가 표에 계속 쌓인다.
            throw new IllegalArgumentException("한 칸은 병합할 수 없습니다");
        }
    }
}
