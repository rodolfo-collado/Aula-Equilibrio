package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.config.HibernateUtil;
import com.practice.aulaequilibrio.model.DashboardData.VersionOption;
import com.practice.aulaequilibrio.model.ScheduleReportData.Interval;
import com.practice.aulaequilibrio.model.ScheduleReportData.RoomOccupancyRow;
import com.practice.aulaequilibrio.model.ScheduleReportData.SessionRow;
import com.practice.aulaequilibrio.model.ScheduleReportData.TeacherInfo;
import com.practice.aulaequilibrio.model.ScheduleReportData.TeacherLoadFacts;
import org.hibernate.SessionFactory;

import java.util.List;
import java.util.function.Supplier;

public class ScheduleReportDAO {
    private final Supplier<SessionFactory> factory;

    public ScheduleReportDAO() { factory = HibernateUtil::getSessionFactory; }
    public ScheduleReportDAO(SessionFactory factory) { this.factory = () -> factory; }

    public List<VersionOption> versions() {
        return factory.get().fromTransaction(session -> session.createSelectionQuery("""
                select v.id, v.name, v.createdOn from ScheduleVersion v
                order by v.createdOn desc, v.id desc
                """, VersionOption.class).getResultList());
    }

    /** Sesiones con horas definidas; las pendientes de revisión quedan fuera del horario impreso. */
    public List<SessionRow> sessions(int versionId) {
        return factory.get().fromTransaction(session -> session.createSelectionQuery("""
                select g.id, g.nombre, t.id, t.name, c.id, c.codigo, s.nombre, a.day, a.startTime, a.endTime
                from ScheduleAssignment a join a.group g join a.teacher t join a.classroom c join a.subject s
                where a.version.id = :version and a.startTime is not null and a.endTime is not null
                order by a.day, a.startTime, c.codigo
                """, SessionRow.class).setParameter("version", versionId).getResultList());
    }

    public List<RoomOccupancyRow> roomOccupancy(int versionId, Integer day) {
        return factory.get().fromTransaction(session -> session.createSelectionQuery("""
                select c.codigo, t.name, c.capacidad, c.state, count(a.id),
                       coalesce(sum(timestampdiff(minute, a.startTime, a.endTime)), 0L)
                from Classroom c join c.type t
                left join ScheduleAssignment a on a.classroom = c and a.version.id = :version
                    and a.endTime > a.startTime and a.day = coalesce(:day, a.day)
                group by c.id, c.codigo, t.name, c.capacidad, c.state
                order by c.codigo
                """, RoomOccupancyRow.class)
                .setParameter("version", versionId).setParameter("day", day).getResultList());
    }

    /** Docentes, sus sesiones en la versión y sus intervalos de disponibilidad definidos. */
    public TeacherLoadFacts teacherLoad(int versionId) {
        return factory.get().fromTransaction(session -> new TeacherLoadFacts(
                session.createSelectionQuery("select t.id, t.name, t.active from Teacher t order by t.name, t.id",
                        TeacherInfo.class).getResultList(),
                session.createSelectionQuery("""
                        select a.teacher.id, a.day, a.startTime, a.endTime from ScheduleAssignment a
                        where a.version.id = :version and a.startTime is not null and a.endTime > a.startTime
                        """, Interval.class).setParameter("version", versionId).getResultList(),
                session.createSelectionQuery("""
                        select a.teacher.id, a.day, a.startTime, a.endTime from Availability a
                        where a.startTime is not null and a.endTime > a.startTime
                        """, Interval.class).getResultList()));
    }
}
