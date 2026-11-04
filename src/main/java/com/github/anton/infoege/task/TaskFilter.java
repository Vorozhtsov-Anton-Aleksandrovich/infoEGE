package com.github.anton.infoege.task;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;

/**
 * Фильтры банка заданий. Пустое поле означает «не фильтровать».
 *
 * @param archive ACTIVE (по умолчанию), ARCHIVED или ALL
 */
public record TaskFilter(Integer egeNumber, String topic, Integer difficulty, Archive archive) {

    public enum Archive { ACTIVE, ARCHIVED, ALL }

    public Archive archiveOrDefault() {
        return archive == null ? Archive.ACTIVE : archive;
    }

    Specification<Task> toSpecification() {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (egeNumber != null) {
                predicates.add(cb.equal(root.get("egeNumber"), egeNumber));
            }
            if (topic != null && !topic.isBlank()) {
                predicates.add(cb.equal(root.get("topic"), topic));
            }
            if (difficulty != null) {
                predicates.add(cb.equal(root.get("difficulty"), difficulty));
            }
            switch (archiveOrDefault()) {
                case ACTIVE -> predicates.add(cb.isFalse(root.get("archived")));
                case ARCHIVED -> predicates.add(cb.isTrue(root.get("archived")));
                case ALL -> { }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
