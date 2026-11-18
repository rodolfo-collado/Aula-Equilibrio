package com.practice.aulaequilibrio.dao;

import com.practice.aulaequilibrio.config.HibernateUtil;
import com.practice.aulaequilibrio.model.DashboardData.VersionOption;
import com.practice.aulaequilibrio.model.ScheduleReportData.SessionRow;
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
}
