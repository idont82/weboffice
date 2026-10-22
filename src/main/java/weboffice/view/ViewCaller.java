package weboffice.view;

import java.util.List;

/**
 * 뷰어를 부를 수 있는 시스템 한 곳.
 *
 * <p>키만으로는 어느 시스템인지 이력에 적을 수 없다. 이름을 함께 둔다.
 */
public class ViewCaller {

    private String name;
    private String key;
    private List<String> addresses = List.of();

    public String name() {
        return name;
    }

    public String key() {
        return key;
    }

    /** 부를 수 있는 주소 대역(CIDR). 비면 주소를 가리지 않는다. */
    public List<String> addresses() {
        return addresses;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setAddresses(List<String> addresses) {
        this.addresses = addresses == null ? List.of() : addresses;
    }
}
