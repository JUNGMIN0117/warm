package com.personalcolor.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * 계절 키워드.
 *
 * <p>대리키 없이 복합키({@code season_code}, {@code display_order})를 쓴다.
 * 순서가 곧 정체성인 값이라 별도 id를 둘 이유가 없다.
 */
@Entity
@Table(name = "season_keywords")
@IdClass(SeasonKeywordEntity.Key.class)
public class SeasonKeywordEntity {

    @Id
    @Column(name = "season_code", length = 20)
    private String seasonCode;

    @Id
    @Column(name = "display_order")
    private int displayOrder;

    @Column(nullable = false, length = 30)
    private String keyword;

    protected SeasonKeywordEntity() {
        // JPA 전용
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public String getKeyword() {
        return keyword;
    }

    /** 복합키 클래스. JPA가 요구하는 형태다. */
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
