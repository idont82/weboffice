package weboffice.workbook;

/** 문서에 대한 권한. wo_workbook_member_map.ROLE_CODE 에 이름 그대로 들어간다. */
public enum WorkbookRole {
    OWNER, EDITOR, VIEWER;

    public boolean canWrite() {
        return this != VIEWER;
    }
}
