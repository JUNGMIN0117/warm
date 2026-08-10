package com.personalcolor.domain.analysis;

import java.util.Locale;

/**
 * 8비트 RGB 색.
 *
 * <p>{@code int[]}나 {@code String}이 아니라 타입을 만든 이유는 두 가지다.
 * 범위 검증을 한 곳에 모을 수 있고, HEX 변환처럼 색에 대한 연산이
 * 흩어지지 않는다. 대표 피부색은 DB에도 HEX로 저장되고 UI에도 색 칩으로
 * 그대로 나가므로 변환이 여러 곳에서 필요하다.
 */
public record RgbColor(int red, int green, int blue) {

    public RgbColor {
        requireByte(red, "red");
        requireByte(green, "green");
        requireByte(blue, "blue");
    }

    private static void requireByte(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + "는 0~255여야 합니다: " + value);
        }
    }

    /** {@code #RRGGBB} 형식. 대문자로 통일해 DB 비교와 UI 표기가 갈리지 않게 한다. */
    public String toHex() {
        return String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue);
    }

    /**
     * {@code #RRGGBB} 문자열을 파싱한다.
     *
     * @throws IllegalArgumentException 형식이 다르거나 16진수가 아닌 경우
     */
    public static RgbColor fromHex(String hex) {
        if (hex == null || hex.length() != 7 || hex.charAt(0) != '#') {
            throw new IllegalArgumentException("#RRGGBB 형식이어야 합니다: " + hex);
        }
        try {
            return new RgbColor(
                    Integer.parseInt(hex.substring(1, 3), 16),
                    Integer.parseInt(hex.substring(3, 5), 16),
                    Integer.parseInt(hex.substring(5, 7), 16));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("16진수가 아닙니다: " + hex, e);
        }
    }
}
