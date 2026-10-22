package weboffice.excel;

/**
 * 엑셀 명암(tint)을 색에 입힌다.
 *
 * <p>엑셀은 테마 색을 그대로 쓰지 않고 "얼마나 밝게/어둡게" 를 함께 적는다. 흰색
 * 배경1 에 -0.15 를 적으면 화면에는 회색(#D9D9D9)이 나온다. 명암을 안 보면 그
 * 칸이 흰색이 되어 칠이 통째로 사라진다(2026-08-19 지적).
 *
 * <p>엑셀은 밝기(HSL 의 L)만 건드린다. 채널마다 따로 계산하면 색이 있는 테마에서
 * 색상이 틀어진다 - POI 의 {@code getRGBWithTint} 가 그렇게 하고, 값도 버림이라
 * 한 단계씩 어긋난다(#D8D8D8, #3F3F3F).
 */
final class Tint {

    private Tint() {
    }

    /** 명암을 입힌 색. {@code tint} 가 0 이면 그대로 돌려준다. */
    static byte[] applied(byte[] rgb, double tint) {
        if (rgb == null || rgb.length < 3 || tint == 0) {
            return rgb;
        }
        double red = (rgb[0] & 0xFF) / 255d;
        double green = (rgb[1] & 0xFF) / 255d;
        double blue = (rgb[2] & 0xFF) / 255d;

        double most = Math.max(red, Math.max(green, blue));
        double least = Math.min(red, Math.min(green, blue));
        double light = (most + least) / 2;
        double saturation = 0;
        double hue = 0;
        if (most != least) {
            double span = most - least;
            saturation = light > 0.5 ? span / (2 - most - least) : span / (most + least);
            if (most == red) {
                hue = (green - blue) / span + (green < blue ? 6 : 0);
            } else if (most == green) {
                hue = (blue - red) / span + 2;
            } else {
                hue = (red - green) / span + 4;
            }
            hue /= 6;
        }

        double lit = tint < 0 ? light * (1 + tint) : light * (1 - tint) + tint;
        lit = Math.min(1, Math.max(0, lit));

        double high = lit < 0.5 ? lit * (1 + saturation) : lit + saturation - lit * saturation;
        double low = 2 * lit - high;
        return new byte[] {
            channel(low, high, hue + 1d / 3),
            channel(low, high, hue),
            channel(low, high, hue - 1d / 3),
        };
    }

    private static byte channel(double low, double high, double at) {
        double point = at < 0 ? at + 1 : at > 1 ? at - 1 : at;
        double value;
        if (point < 1d / 6) {
            value = low + (high - low) * 6 * point;
        } else if (point < 1d / 2) {
            value = high;
        } else if (point < 2d / 3) {
            value = low + (high - low) * (2d / 3 - point) * 6;
        } else {
            value = low;
        }
        return (byte) Math.round(value * 255);
    }
}
