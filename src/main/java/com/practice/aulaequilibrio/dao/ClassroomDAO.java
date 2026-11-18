package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.config.HibernateUtil;
import com.practice.aulaequilibrio.model.Classroom;
import com.practice.aulaequilibrio.model.ClassroomStatus;
import com.practice.aulaequilibrio.model.ClassroomType;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Locale;

public class ClassroomDAO {
    public List<Classroom> listar() {
        return list(null);
    }

    public List<Classroom> listarPorTipo(Integer typeId) {
        return list(typeId);
    }

    private List<Classroom> list(Integer typeId) {
        return HibernateUtil.getSessionFactory().fromTransaction(session -> {
            String hql = "from Classroom c join fetch c.type";
            if (typeId != null) hql += " where c.type.id = :type";
            var query = session.createSelectionQuery(hql + " order by c.codigo", Classroom.class);
            if (typeId != null) query.setParameter("type", typeId);
            return query.getResultList();
        });
    }

    public void guardar(Classroom value) {
        save(value.getId(), value.getRevision(), value.getCodigo(), value.getCapacidad(),
                value.getType() == null ? null : value.getType().getId(), value.getState());
    }

    public void save(Integer id, long revision, String code, Integer capacity, Integer typeId, ClassroomStatus state) {
        String normalized = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        if (normalized.isBlank() || normalized.length() > 20) {
            throw new IllegalArgumentException("El código del aula es obligatorio y admite hasta 20 caracteres.");
        }
        if (capacity == null || capacity <= 0) {
            throw new IllegalArgumentException("La capacidad debe ser un entero mayor que cero.");
        }
        if (typeId == null || state == null) {
            throw new IllegalArgumentException("Selecciona el tipo y el estado del aula.");
        }
        HibernateUtil.getSessionFactory().inTransaction(session -> {
            ClassroomType type = session.find(ClassroomType.class, typeId, LockModeType.PESSIMISTIC_READ);
            if (type == null) throw new IllegalArgumentException("El tipo ya no existe. Actualiza la lista.");
            long duplicates = session.createSelectionQuery("""
                    select count(c) from Classroom c where upper(c.codigo) = :code
                    and (:id is null or c.id <> :id)
                    """, Long.class).setParameter("code", normalized).setParameter("id", id).getSingleResult();
            if (duplicates > 0) throw new IllegalArgumentException("Ya existe un aula con ese código.");
            Classroom current = id == null ? new Classroom()
                    : session.find(Classroom.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (current == null) throw new IllegalArgumentException("El aula ya no existe.");
            if (id != null && current.getRevision() != revision) {
                throw new IllegalArgumentException("Otra persona modificó el aula. Actualiza la tabla.");
            }
            if (id != null) {
                long assigned = session.createSelectionQuery(
                        "select count(a) from ScheduleAssignment a where a.classroom.id = :id", Long.class)
                        .setParameter("id", id).getSingleResult();
                if (assigned > 0 && state != ClassroomStatus.DISPONIBLE) {
                    throw new IllegalArgumentException("Retira primero las asignaciones de horario para inhabilitar el aula.");
                }
                var capacities = session.createSelectionQuery("""
                        select a.group from ScheduleAssignment a where a.classroom.id = :id
                        """, com.practice.aulaequilibrio.model.Groupe.class).setParameter("id", id).getResultList();
                for (var group : capacities) {
                    int needed = group.getSubject() == null ? group.getCantidadEstudiantes() : group.getCapacity();
                    if (capacity < needed) {
                        throw new IllegalArgumentException("La capacidad quedaría por debajo del cupo de un grupo asignado.");
                    }
                }
            }
            current.setCodigo(normalized);
            current.setCapacidad(capacity);
            current.setType(type);
            current.setState(state);
            if (id == null) session.persist(current);
        });
    }

    public void eliminar(Classroom value) {
        HibernateUtil.getSessionFactory().inTransaction(session -> {
            Classroom current = session.find(Classroom.class, value.getId(), LockModeType.PESSIMISTIC_WRITE);
            if (current == null) throw new IllegalArgumentException("El aula ya no existe.");
            if (current.getRevision() != value.getRevision()) {
                throw new IllegalArgumentException("El aula cambió. Actualiza la tabla.");
            }
            session.remove(current);
        });
    }
}
