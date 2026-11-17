package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.config.HibernateUtil;
import com.practice.aulaequilibrio.model.Career;
import com.practice.aulaequilibrio.model.CareerSubject;
import com.practice.aulaequilibrio.model.CareerSubjectId;
import com.practice.aulaequilibrio.model.Faculty;
import com.practice.aulaequilibrio.model.FacultySummary;
import com.practice.aulaequilibrio.model.Subject;
import com.practice.aulaequilibrio.util.ErrorMessages;
import com.practice.aulaequilibrio.util.ValidationException;
import org.hibernate.Session;

import java.util.List;
import java.util.function.Function;

public final class AcademicStructureDAO {
    public List<FacultySummary> listFaculties() {
        return transact(session -> session.createSelectionQuery("""
                select f.id, f.code, f.name, f.active, count(c)
                from Faculty f left join f.careers c
                group by f.id, f.code, f.name, f.active
                order by f.name, f.id
                """, FacultySummary.class).getResultList());
    }

    public List<Career> listCareers(Long facultyId) {
        return transact(session -> {
            String hql = "from Career c join fetch c.faculty f";
            if (facultyId != null) {
                hql += " where f.id = :facultyId";
            }
            var query = session.createSelectionQuery(hql + " order by c.name, c.id", Career.class);
            if (facultyId != null) {
                query.setParameter("facultyId", facultyId);
            }
            return query.getResultList();
        });
    }

    public List<CareerSubject> listStudyPlan(long careerId) {
        return transact(session -> {
            require(session, Career.class, careerId, "La carrera");
            return session.createSelectionQuery("""
                    select link from CareerSubject link
                    join fetch link.subject subject
                    join fetch link.career career
                    join fetch career.faculty
                    where career.id = :careerId
                    order by link.recommendedLevel nulls last, subject.codigo
                    """, CareerSubject.class).setParameter("careerId", careerId).getResultList();
        });
    }

    public Faculty saveFaculty(Long id, String code, String name, boolean active) {
        return save("la facultad", session -> {
            Faculty faculty = id == null ? new Faculty() : require(session, Faculty.class, id, "La facultad");
            faculty.setCode(code);
            faculty.setName(name);
            faculty.setActive(active);
            if (id == null) {
                session.persist(faculty);
            }
            return faculty;
        });
    }

    public Career saveCareer(Long id, long facultyId, String code, String name, boolean active) {
        return save("la carrera", session -> {
            Faculty faculty = require(session, Faculty.class, facultyId, "La facultad");
            Career career = id == null ? new Career() : require(session, Career.class, id, "La carrera");
            career.setFaculty(faculty);
            career.setCode(code);
            career.setName(name);
            career.setActive(active);
            if (id == null) {
                session.persist(career);
            }
            return career;
        });
    }

    public void deleteFaculty(long id) {
        delete("la facultad", session -> require(session, Faculty.class, id, "La facultad"));
    }

    public void deleteCareer(long id) {
        delete("la carrera", session -> require(session, Career.class, id, "La carrera"));
    }

    public void addSubject(long careerId, int subjectId, Short recommendedLevel) {
        save("la asignatura del plan", session -> {
            Career career = require(session, Career.class, careerId, "La carrera");
            Subject subject = require(session, Subject.class, subjectId, "La asignatura");
            if (session.find(CareerSubject.class, new CareerSubjectId(careerId, subjectId)) != null) {
                throw new ValidationException("La asignatura ya pertenece al plan de esta carrera.");
            }
            session.persist(new CareerSubject(career, subject, recommendedLevel));
            return null;
        });
    }

    public void updateRecommendedLevel(long careerId, int subjectId, Short recommendedLevel) {
        transact(session -> {
            require(session, CareerSubject.class, new CareerSubjectId(careerId, subjectId),
                    "La asignatura del plan").setRecommendedLevel(recommendedLevel);
            return null;
        });
    }

    public void removeSubject(long careerId, int subjectId) {
        delete("la asignatura del plan", session -> require(session, CareerSubject.class,
                new CareerSubjectId(careerId, subjectId), "La asignatura del plan"));
    }

    private static <T> T require(Session session, Class<T> type, Object id, String label) {
        T entity = session.find(type, id);
        if (entity == null) {
            throw new ValidationException(label + " ya no existe. Actualiza la lista.");
        }
        return entity;
    }

    private static <T> T transact(Function<Session, T> operation) {
        return HibernateUtil.getSessionFactory().fromTransaction(operation);
    }

    private static <T> T save(String label, Function<Session, T> operation) {
        try {
            return transact(operation);
        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw ErrorMessages.onSave(label, exception);
        }
    }

    private static void delete(String label, Function<Session, ?> find) {
        try {
            transact(session -> {
                session.remove(find.apply(session));
                return null;
            });
        } catch (ValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw ErrorMessages.onDelete(label, exception);
        }
    }
}
