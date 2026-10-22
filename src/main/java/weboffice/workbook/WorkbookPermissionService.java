package weboffice.workbook;

import org.springframework.stereotype.Service;

import weboffice.security.UserContext;
import weboffice.sheet.SheetMapper;
import weboffice.sheet.SheetSummaryDto;

/**
 * 문서 권한 판정.
 *
 * <p>없는 시트도 ForbiddenException 으로 답한다. 404 와 403 을 구분해 주면
 * 권한 없는 사용자가 시트 번호를 훑어 문서 존재 여부를 알아낼 수 있다.
 *
 * <p>사번을 받는 판정과 현재 사용자로 판정하는 것을 나눠 둔다. 테스트가
 * ThreadLocal 을 손대지 않고 여러 사용자를 시험할 수 있어야 한다.
 */
@Service
public class WorkbookPermissionService {

    private final SheetMapper sheetMapper;
    private final WorkbookMapper workbookMapper;
    private final MemberMapper memberMapper;

    public WorkbookPermissionService(SheetMapper sheetMapper, WorkbookMapper workbookMapper,
                                     MemberMapper memberMapper) {
        this.sheetMapper = sheetMapper;
        this.workbookMapper = workbookMapper;
        this.memberMapper = memberMapper;
    }

    public WorkbookRole requireRead(Long sheetSeq) {
        return requireRead(sheetSeq, UserContext.current().userKey());
    }

    public WorkbookRole requireWrite(Long sheetSeq) {
        return requireWrite(sheetSeq, UserContext.current().userKey());
    }

    public WorkbookRole requireRead(Long sheetSeq, String userKey) {
        return resolveRole(sheetSeq, userKey);
    }

    public WorkbookRole requireWrite(Long sheetSeq, String userKey) {
        WorkbookRole role = resolveRole(sheetSeq, userKey);
        if (!role.canWrite()) {
            throw new ForbiddenException("읽기 전용 권한입니다");
        }
        return role;
    }

    /** 문서 단위 판정. export 처럼 시트가 아니라 문서 전체를 다루는 경로가 쓴다. */
    public WorkbookRole requireReadWorkbook(Long workbookSeq) {
        return requireReadWorkbook(workbookSeq, UserContext.current().userKey());
    }

    public WorkbookRole requireReadWorkbook(Long workbookSeq, String userKey) {
        return resolveRoleByWorkbook(workbookSeq, userKey);
    }

    /**
     * 소유자 전용 판정.
     *
     * <p>구성원과 공개 링크는 소유자만 바꾼다. 편집자가 남을 초대할 수 있으면
     * 소유자가 통제를 잃는다(설계 9절).
     */
    public void requireOwner(Long workbookSeq) {
        if (requireReadWorkbook(workbookSeq) != WorkbookRole.OWNER) {
            throw new ForbiddenException("문서 소유자만 할 수 있습니다");
        }
    }

    /** 문서 단위 쓰기 판정. 이미지 업로드처럼 시트가 아니라 문서에 속하는 자원이 쓴다. */
    public WorkbookRole requireWriteWorkbook(Long workbookSeq) {
        return requireWriteWorkbook(workbookSeq, UserContext.current().userKey());
    }

    public WorkbookRole requireWriteWorkbook(Long workbookSeq, String userKey) {
        WorkbookRole role = resolveRoleByWorkbook(workbookSeq, userKey);
        if (!role.canWrite()) {
            throw new ForbiddenException("읽기 전용 권한입니다");
        }
        return role;
    }

    private WorkbookRole resolveRole(Long sheetSeq, String userKey) {
        SheetSummaryDto sheet = sheetMapper.selectSheet(sheetSeq);
        if (sheet == null) {
            throw new ForbiddenException("접근 권한이 없습니다");
        }
        return resolveRoleByWorkbook(sheet.workbookSeq(), userKey);
    }

    private WorkbookRole resolveRoleByWorkbook(Long workbookSeq, String userKey) {
        WorkbookDto workbook = workbookMapper.selectWorkbook(workbookSeq);
        if (workbook == null) {
            throw new ForbiddenException("접근 권한이 없습니다");
        }
        // 소유자는 구성원 테이블에 등록되지 않아도 OWNER 다. import 직후 자기 문서에
        // 못 들어가는 상태를 만들지 않기 위해서다.
        if (userKey.equals(workbook.ownerUser())) {
            return WorkbookRole.OWNER;
        }

        String roleCode = memberMapper.selectRole(workbookSeq, userKey);
        WorkbookRole memberRole = roleCode == null ? null : WorkbookRole.valueOf(roleCode);

        // 세션의 허가만 믿지 않고 토큰이 아직 그대로인지 매번 확인한다. 그러지
        // 않으면 해제·재발급이 이미 들어와 있는 세션에 안 먹는다(설계 6절).
        //
        // "토큰이 있는가" 가 아니라 "들어올 때 쓴 그 토큰인가" 를 본다. 있기만
        // 보면 재발급이 안 먹는다 - 새 토큰이 채워져 조건이 계속 참이다(2026-08-07 실측).
        String redeemed = UserContext.currentGrants().get(workbookSeq);
        boolean grantedByLink = redeemed != null && redeemed.equals(workbook.shareToken());

        // 순서가 중요하다. 링크가 주는 권한(편집)이 VIEWER 보다 넓으므로 VIEWER 를
        // 그대로 돌려주면 초대받은 쪽이 오히려 손해를 본다(설계 7절). 반대로 링크를
        // 맨 앞에 두면 OWNER 를 덮는다.
        if (memberRole != null && memberRole.canWrite()) {
            return memberRole;
        }
        if (grantedByLink) {
            return WorkbookRole.EDITOR;
        }
        if (memberRole != null) {
            return memberRole;
        }
        throw new ForbiddenException("접근 권한이 없습니다");
    }
}
