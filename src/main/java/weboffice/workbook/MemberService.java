package weboffice.workbook;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import weboffice.common.AuditUser;
import weboffice.security.DirectoryUser;
import weboffice.security.UserDirectory;

/**
 * 구성원 관리. USER_ID 와 신원 열쇠를 옮겨 담는 곳이다.
 *
 * <p>바깥(브라우저)은 USER_ID 로 말하고, 안(DB)은 신원 열쇠로 적는다. 감사 컬럼·
 * 잠금·소유자가 전부 열쇠라 구성원만 다른 키를 쓰면 아귀가 안 맞는다(설계 3절).
 */
@Service
public class MemberService {

    private final MemberMapper memberMapper;
    private final WorkbookMapper workbookMapper;
    private final UserDirectory userDirectory;

    public MemberService(MemberMapper memberMapper, WorkbookMapper workbookMapper,
                         UserDirectory userDirectory) {
        this.memberMapper = memberMapper;
        this.workbookMapper = workbookMapper;
        this.userDirectory = userDirectory;
    }

    /** 구성원 목록. 저장된 열쇠로 인명부를 조회해 이름을 채운다. */
    public List<WorkbookMemberDto> list(Long workbookSeq) {
        List<MemberRow> rows = memberMapper.selectMembers(workbookSeq);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<String, DirectoryUser> byKey = userDirectory
                .findByKeys(rows.stream().map(MemberRow::memberUser).toList())
                .stream()
                .collect(Collectors.toMap(DirectoryUser::userKey, user -> user,
                        (first, second) -> first));

        return rows.stream().map(row -> {
            DirectoryUser user = byKey.get(row.memberUser());
            // 못 찾아도 줄은 남긴다. 사라지면 제거 버튼도 같이 사라진다.
            return new WorkbookMemberDto(
                    user == null ? null : user.userId(),
                    user == null ? null : user.displayName(),
                    user == null ? null : user.rank(),
                    user == null ? null : user.deptName(),
                    row.roleCode());
        }).toList();
    }

    /** 있으면 역할 변경, 없으면 추가. 화면에서 둘을 나눌 이유가 없다. */
    @Transactional
    public void put(Long workbookSeq, String userId, String role, AuditUser actor) {
        WorkbookRole parsed = parseRole(role);
        if (parsed == WorkbookRole.OWNER) {
            throw new IllegalArgumentException("소유자 권한은 부여할 수 없습니다");
        }

        String userKey = resolveUserKey(userId);
        if (userKey.equals(workbookMapper.selectWorkbook(workbookSeq).ownerUser())) {
            // 넣으면 목록에 두 번 나오고 역할이 둘이 된다.
            throw new IllegalArgumentException("소유자는 구성원으로 넣을 수 없습니다");
        }

        memberMapper.upsertMember(workbookSeq, userKey, parsed.name(), actor.userKey());
    }

    @Transactional
    public void remove(Long workbookSeq, String userId, AuditUser actor) {
        memberMapper.softDeleteMember(workbookSeq, resolveUserKey(userId), actor.userKey());
    }

    /** ROLE_CODE 에 아무 문자열이나 들어가면 나중에 권한 판정에서 터진다. */
    private WorkbookRole parseRole(String role) {
        try {
            return WorkbookRole.valueOf(role);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("알 수 없는 권한입니다: " + role);
        }
    }

    private String resolveUserKey(String userId) {
        DirectoryUser user = userDirectory.findByUserId(userId);
        if (user == null) {
            throw new IllegalArgumentException("찾을 수 없는 사용자입니다: " + userId);
        }
        return user.userKey();
    }
}
