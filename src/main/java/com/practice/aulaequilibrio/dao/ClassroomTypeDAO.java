package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.config.HibernateUtil;
import com.practice.aulaequilibrio.model.ClassroomType;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Locale;

public class ClassroomTypeDAO {
    public List<ClassroomType> listar() {
        return HibernateUtil.getSessionFactory().fromTransaction(session ->
                session.createSelectionQuery("from ClassroomType order by name", ClassroomType.class).getResultList());
    }

    public void guardar(ClassroomType value) {
        save(value.getId(), value.getRevision(), value.getName(), value.getDescription());
    }

    public void save(Integer id, long revision, String name, String description) {
        String normalized = name == null ? "" : name.strip();
        String detail = description == null ? "" : description.strip();
        if (normalized.isBlank() || normalized.length() > 60) {
            throw new IllegalArgumentException("El nombre del tipo es obligatorio y admite hasta 60 caracteres.");
        }
        if (detail.length() > 160) {
            throw new IllegalArgumentException("La descripción admite hasta 160 caracteres.");
        }
        HibernateUtil.getSessionFactory().inTransaction(session -> {
            long duplicates = session.createSelectionQuery("""
                    select count(t) from ClassroomType t where lower(t.name) = :name
                    and (:id is null or t.id <> :id)
                    """, Long.class).setParameter("name", normalized.toLowerCase(Locale.ROOT))
                    .setParameter("id", id).getSingleResult();
            if (duplicates > 0) throw new IllegalArgumentException("Ya existe un tipo con ese nombre.");
            ClassroomType current = id == null ? new ClassroomType()
                    : session.find(ClassroomType.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (current == null) throw new IllegalArgumentException("El tipo ya no existe.");
            if (id != null && current.getRevision() != revision) {
                throw new IllegalArgumentException("Otra persona modificó el tipo. Actualiza la tabla.");
            }
            current.setName(normalized);
            current.setDescription(detail);
            if (id == null) session.persist(current);
        });
    }

    public void eliminar(ClassroomType value) {
        HibernateUtil.getSessionFactory().inTransaction(session -> {
            ClassroomType current = session.find(ClassroomType.class, value.getId(), LockModeType.PESSIMISTIC_WRITE);
            if (current == null) throw new IllegalArgumentException("El tipo ya no existe.");
            if (current.getRevision() != value.getRevision()) {
                throw new IllegalArgumentException("El tipo cambió. Actualiza la tabla.");
            }
            long rooms = session.createSelectionQuery(
                    "select count(c) from Classroom c where c.type.id = :id", Long.class)
                    .setParameter("id", value.getId()).getSingleResult();
            if (rooms > 0) {
                throw new IllegalArgumentException("El tipo tiene aulas asociadas. Reasígnalas o elimínalas primero.");
            }
            session.remove(current);
        });
    }
}
