package com.personalcolor.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** 스타일링 팁. 키워드와 같은 이유로 복합키를 쓴다. */
@Entity
@Table(name = "styling_tips")
@IdClass(StylingTipEntity.Key.class)
public class StylingTipEntity {

    @Id
    @Column(name = "season_code", length = 20)
    private String seasonCode;

    @Id
    @Column(name = "display_order")
    private int displayOrder;

    @Column(nullable = false)
    private String tip;

    protected StylingTipEntity() {
        // JPA 전용
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public String getTip() {
        return tip;
    }

    /** 복합키 클래스. */
    public static class Key implements Serializable {
        private String seasonCode;
        private int displayOrder;

        public Key() {}

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return displayOrder == key.displayOrder
                    && Objects.equals(seasonCode, key.seasonCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(seasonCode, displayOrder);
        }
    }
}
