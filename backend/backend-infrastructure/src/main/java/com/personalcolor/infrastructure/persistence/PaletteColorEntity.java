package com.personalcolor.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 팔레트 한 칸. */
@Entity
@Table(name = "palette_colors")
public class PaletteColorEntity {

    /** 추천/기피 구분. DB의 CHECK 제약과 값이 일치해야 한다. */
    public enum Kind {
        BEST,
        WORST
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "palette_kind", nullable = false, length = 10)
    private String paletteKind;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false, length = 7)
    private String hex;

    protected PaletteColorEntity() {
        // JPA 전용
    }

    public Kind kind() {
        return Kind.valueOf(paletteKind);
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public String getName() {
        return name;
    }

    public String getHex() {
        return hex;
    }
}
