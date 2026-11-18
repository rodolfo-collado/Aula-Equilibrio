package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.config.HibernateUtil;
import com.practice.aulaequilibrio.model.DashboardData.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.function.Supplier;

public class DashboardDAO {
    private final Supplier<SessionFactory> factory;

    public DashboardDAO() { factory = HibernateUtil::getSessionFactory; }
    public DashboardDAO(SessionFactory factory) { this.factory = () -> factory; }

    public Facts load(Integer versionId) {
        return factory.get().fromTransaction(session -> {
            var faculties = session.createSelectionQuery("""
                    select f.id, f.name, f.active, count(c.id)
                    from Faculty f left join f.careers c
                    group by f.id, f.name, f.active order by f.name, f.id
                    """, FacultyInfo.class).getResultList();
            var links = session.createSelectionQuery("""
                    select distinct c.faculty.id, link.subject.id
                    from CareerSubject link join link.career c
                    """, SubjectLink.class).getResultList();
            var groups = session.createSelectionQuery("""
                    select g.id, s.id, t.id, s.sesionesRequeridas
                    from Groupe g left join g.subject s left join g.teacher t
                    """, GroupInfo.class).getResultList();
            var rooms = session.createSelectionQuery(
                    "select c.id, c.capacidad, c.state from Classroom c", RoomInfo.class).getResultList();
            var versions = session.createSelectionQuery("""
                    select v.id, v.name, v.createdOn from ScheduleVersion v
                    order by v.createdOn desc, v.id desc
                    """, VersionOption.class).getResultList();
            VersionOption selected = versions.stream().filter(v -> v.id().equals(versionId)).findFirst()
                    .orElse(versions.isEmpty() ? null : versions.getFirst());
            List<AssignmentInfo> assignments = selected == null ? List.of() : session.createSelectionQuery("""
                    select a.group.id, a.subject.id, a.classroom.id from ScheduleAssignment a
                    where a.version.id = :version
                    """, AssignmentInfo.class).setParameter("version", selected.id()).getResultList();
            return new Facts(faculties, links, groups, rooms, versions, selected, assignments,
                    count(session, "select count(s) from Subject s"),
                    count(session, "select count(t) from Teacher t"),
                    count(session, "select count(t) from Teacher t where t.active = true"),
                    count(session, """
                            select count(t) from Teacher t where t.active = true
                            and not exists (select a.id from Availability a where a.teacher = t)
                            """),
                    count(session, """
                            select count(s) from Subject s
                            where not exists (select link.id from CareerSubject link where link.subject = s)
                            """));
        });
    }

    private static long count(Session session, String hql) {
        return session.createSelectionQuery(hql, Long.class).getSingleResult();
    }
}
