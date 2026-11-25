package com.practice.aulaequilibrio.service;

import com.practice.aulaequilibrio.api.ResendClient;
import com.practice.aulaequilibrio.dao.ScheduleReportDAO;
import com.practice.aulaequilibrio.model.Permission;
import com.practice.aulaequilibrio.report.ReportEngine.Format;
import com.practice.aulaequilibrio.report.ReportEngine.Document;
import com.practice.aulaequilibrio.util.ValidationException;

public final class ScheduleEmailService {
    private final AccessControl access = AccessControl.current();
    private final ScheduleReportDAO reports = new ScheduleReportDAO();
    private ResendClient resend;

    public String recipient(int teacherId) {
        access.require(Permission.SEND_SCHEDULE_EMAIL);
        String email = reports.teacherEmail(teacherId);
        if (email == null || email.isBlank()) {
            throw new ValidationException("Este docente no tiene un correo registrado.");
        }
        return email;
    }

    public String send(int teacherId, String confirmedRecipient, String version,
                       Document report) throws Exception {
        if (!recipient(teacherId).equals(confirmedRecipient)) {
            throw new ValidationException("El correo del docente cambió. Vuelve a confirmar el envío.");
        }
        if (resend == null) resend = new ResendClient();
        resend.requireConfiguration();
        return resend.sendSchedule(confirmedRecipient, version, report.bytes(Format.PDF));
    }
}
