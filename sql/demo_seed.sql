-- Ejemplos institucionales. Agrega lo que falte sin reemplazar registros existentes.
DO $seed$
BEGIN
    INSERT INTO teacher (name, email) VALUES
        ('Elena Campos', 'elena.demo@example.test'),
        ('Mateo Reyes', 'mateo.demo@example.test'),
        ('Sofía Vargas', 'sofia.demo@example.test'),
        ('Valeria Méndez', 'valeria.demo@example.test'),
        ('Carlos Duarte', 'carlos.demo@example.test'),
        ('Lucía Herrera', 'lucia.demo@example.test'),
        ('Diego Morales', 'diego.demo@example.test'),
        ('Camila Torres', 'camila.demo@example.test')
    ON CONFLICT (email) DO NOTHING;

    INSERT INTO faculty (code, name, active) VALUES
        ('DEMO-ING', 'Facultad de Ingeniería', true),
        ('DEMO-NEG', 'Facultad de Negocios', true),
        ('DEMO-SAL', 'Facultad de Ciencias de la Salud', true)
    ON CONFLICT (code) DO NOTHING;

    INSERT INTO career (id_faculty, code, name, active)
    SELECT faculty.id, demo.code, demo.name, true
    FROM (VALUES
        ('DEMO-ING', 'DEMO-SIS', 'Ingeniería en Sistemas'),
        ('DEMO-ING', 'DEMO-IND', 'Ingeniería Industrial'),
        ('DEMO-NEG', 'DEMO-ADM', 'Administración de Empresas'),
        ('DEMO-NEG', 'DEMO-CON', 'Contabilidad y Finanzas'),
        ('DEMO-SAL', 'DEMO-ENF', 'Enfermería')
    ) AS demo(faculty_code, code, name)
    JOIN faculty ON faculty.code = demo.faculty_code
    ON CONFLICT (code) DO NOTHING;

    INSERT INTO asignatura (codigo, nombre, sesiones_requeridas, duration_minutes) VALUES
        ('DEMO-PROG', 'Programación I', 2, 120),
        ('DEMO-MAT', 'Matemática discreta', 2, 120),
        ('DEMO-BD', 'Bases de datos', 2, 120),
        ('DEMO-ALGO', 'Algoritmos y estructuras de datos', 2, 120),
        ('DEMO-FIS', 'Física aplicada', 2, 120),
        ('DEMO-EST', 'Estadística', 2, 120),
        ('DEMO-ADM', 'Fundamentos de administración', 2, 90),
        ('DEMO-CONT', 'Contabilidad general', 2, 120),
        ('DEMO-ANAT', 'Anatomía y fisiología', 2, 120),
        ('DEMO-ETI', 'Ética profesional', 2, 60)
    ON CONFLICT (codigo) DO NOTHING;

    INSERT INTO career_subject (id_career, id_subject, recommended_level, optional)
    SELECT career.id_career, asignatura.id, plan.level, plan.optional
    FROM (VALUES
        ('DEMO-SIS', 'DEMO-PROG', 1, false), ('DEMO-SIS', 'DEMO-MAT', 1, false),
        ('DEMO-SIS', 'DEMO-BD', 2, false), ('DEMO-SIS', 'DEMO-ALGO', 2, false),
        ('DEMO-SIS', 'DEMO-EST', 3, false), ('DEMO-SIS', 'DEMO-ETI', 2, true),
        ('DEMO-IND', 'DEMO-MAT', 1, false), ('DEMO-IND', 'DEMO-FIS', 1, false),
        ('DEMO-IND', 'DEMO-EST', 2, false), ('DEMO-IND', 'DEMO-ADM', 3, true),
        ('DEMO-IND', 'DEMO-ETI', 2, true),
        ('DEMO-ADM', 'DEMO-ADM', 1, false), ('DEMO-ADM', 'DEMO-CONT', 1, false),
        ('DEMO-ADM', 'DEMO-EST', 2, false), ('DEMO-ADM', 'DEMO-ETI', 2, false),
        ('DEMO-CON', 'DEMO-CONT', 1, false), ('DEMO-CON', 'DEMO-ADM', 1, false),
        ('DEMO-CON', 'DEMO-EST', 2, false), ('DEMO-CON', 'DEMO-ETI', 2, false),
        ('DEMO-ENF', 'DEMO-ANAT', 1, false), ('DEMO-ENF', 'DEMO-EST', 2, true),
        ('DEMO-ENF', 'DEMO-ETI', 2, false)
    ) AS plan(career_code, subject_code, level, optional)
    JOIN career ON career.code = plan.career_code
    JOIN asignatura ON asignatura.codigo = plan.subject_code
    ON CONFLICT (id_career, id_subject) DO NOTHING;

    INSERT INTO grupo (nombre, cantidad_estudiantes) VALUES
        ('DEMO-1A', 28), ('DEMO-1B', 24), ('DEMO-2A', 20),
        ('DEMO-2B', 28), ('DEMO-2C', 22), ('DEMO-1C', 30),
        ('DEMO-3A', 32), ('DEMO-3B', 24), ('DEMO-ADM1A', 35),
        ('DEMO-CON1A', 26), ('DEMO-ENF1A', 24), ('DEMO-ETI1A', 28)
    ON CONFLICT (nombre) DO NOTHING;

    UPDATE grupo SET subject_id = asignatura.id, teacher_id = teacher.id, capacity = grupo.cantidad_estudiantes
    FROM (VALUES
        ('DEMO-1A', 'DEMO-PROG', 'elena.demo@example.test'),
        ('DEMO-1B', 'DEMO-MAT', 'mateo.demo@example.test'),
        ('DEMO-2A', 'DEMO-BD', 'sofia.demo@example.test'),
        ('DEMO-2B', 'DEMO-ALGO', 'elena.demo@example.test'),
        ('DEMO-2C', 'DEMO-ALGO', 'elena.demo@example.test'),
        ('DEMO-1C', 'DEMO-FIS', 'mateo.demo@example.test'),
        ('DEMO-3A', 'DEMO-EST', 'valeria.demo@example.test'),
        ('DEMO-3B', 'DEMO-EST', 'valeria.demo@example.test'),
        ('DEMO-ADM1A', 'DEMO-ADM', 'carlos.demo@example.test'),
        ('DEMO-CON1A', 'DEMO-CONT', 'lucia.demo@example.test'),
        ('DEMO-ENF1A', 'DEMO-ANAT', 'diego.demo@example.test'),
        ('DEMO-ETI1A', 'DEMO-ETI', 'camila.demo@example.test')
    ) AS demo(group_code, subject_code, email)
    JOIN asignatura ON asignatura.codigo = demo.subject_code
    JOIN teacher ON teacher.email = demo.email
    WHERE grupo.nombre = demo.group_code AND grupo.subject_id IS NULL AND grupo.teacher_id IS NULL;

    INSERT INTO classroom_type (name, revision) VALUES
        ('Demo · Aula teórica', 0), ('Demo · Laboratorio', 0), ('Demo · Laboratorio de salud', 0)
    ON CONFLICT (name) DO NOTHING;

    INSERT INTO classroom (codigo, capacidad, type_id, revision)
    SELECT demo.codigo, demo.capacidad, tipo.id, 0
    FROM (VALUES
        ('DEMO-A101', 35, 'Demo · Aula teórica'), ('DEMO-L201', 30, 'Demo · Laboratorio'),
        ('DEMO-L202', 26, 'Demo · Laboratorio'), ('DEMO-A102', 40, 'Demo · Aula teórica'),
        ('DEMO-A103', 35, 'Demo · Aula teórica'), ('DEMO-A104', 30, 'Demo · Aula teórica'),
        ('DEMO-L203', 32, 'Demo · Laboratorio'), ('DEMO-S101', 28, 'Demo · Laboratorio de salud')
    ) AS demo(codigo, capacidad, tipo)
    JOIN classroom_type tipo ON tipo.name = demo.tipo
    ON CONFLICT (codigo) DO NOTHING;

    LOCK TABLE availability, schedule_version IN SHARE ROW EXCLUSIVE MODE;

    INSERT INTO availability (teacher_id, weekday, start_time, end_time)
    SELECT teacher.id, demo.day, demo.start_time, demo.end_time
    FROM (VALUES
        ('elena.demo@example.test', 1, TIME '08:00', TIME '10:00'),
        ('elena.demo@example.test', 3, TIME '08:00', TIME '10:00'),
        ('elena.demo@example.test', 1, TIME '10:00', TIME '12:00'),
        ('elena.demo@example.test', 3, TIME '10:00', TIME '12:00'),
        ('elena.demo@example.test', 2, TIME '08:00', TIME '10:00'),
        ('elena.demo@example.test', 4, TIME '08:00', TIME '10:00'),
        ('mateo.demo@example.test', 1, TIME '08:00', TIME '10:00'),
        ('mateo.demo@example.test', 3, TIME '08:00', TIME '10:00'),
        ('mateo.demo@example.test', 2, TIME '10:00', TIME '12:00'),
        ('mateo.demo@example.test', 4, TIME '10:00', TIME '12:00'),
        ('sofia.demo@example.test', 2, TIME '10:00', TIME '12:00'),
        ('sofia.demo@example.test', 4, TIME '10:00', TIME '12:00'),
        ('valeria.demo@example.test', 1, TIME '10:00', TIME '12:00'),
        ('valeria.demo@example.test', 3, TIME '10:00', TIME '12:00'),
        ('valeria.demo@example.test', 2, TIME '13:00', TIME '15:00'),
        ('valeria.demo@example.test', 4, TIME '13:00', TIME '15:00'),
        ('carlos.demo@example.test', 1, TIME '08:00', TIME '10:00'),
        ('carlos.demo@example.test', 3, TIME '08:00', TIME '10:00'),
        ('lucia.demo@example.test', 2, TIME '08:00', TIME '10:00'),
        ('lucia.demo@example.test', 4, TIME '08:00', TIME '10:00'),
        ('diego.demo@example.test', 1, TIME '13:00', TIME '15:00'),
        ('diego.demo@example.test', 3, TIME '13:00', TIME '15:00'),
        ('camila.demo@example.test', 5, TIME '08:00', TIME '10:00'),
        ('camila.demo@example.test', 5, TIME '10:00', TIME '12:00')
    ) AS demo(email, day, start_time, end_time)
    JOIN teacher ON teacher.email = demo.email
    WHERE NOT EXISTS (SELECT 1 FROM availability a WHERE a.teacher_id = teacher.id AND a.weekday = demo.day
        AND a.start_time < demo.end_time AND a.end_time > demo.start_time);

    INSERT INTO schedule_version (name, created_on)
    SELECT 'Demo · Semana académica', CURRENT_DATE
    WHERE NOT EXISTS (SELECT 1 FROM schedule_version WHERE name = 'Demo · Semana académica');

    INSERT INTO schedule_assignment (version_id, group_id, subject_id, teacher_id, classroom_id, day_of_week, start_time, end_time)
    SELECT version.id, grupo.id, asignatura.id, teacher.id, classroom.id, demo.day, demo.start_time, demo.start_time + make_interval(mins => asignatura.duration_minutes)
    FROM (VALUES
        ('DEMO-1A', 'DEMO-L201', 1, TIME '08:00'), ('DEMO-1A', 'DEMO-L201', 3, TIME '08:00'),
        ('DEMO-1B', 'DEMO-A101', 1, TIME '08:00'), ('DEMO-1B', 'DEMO-A101', 3, TIME '08:00'),
        ('DEMO-2A', 'DEMO-L202', 2, TIME '10:00'), ('DEMO-2A', 'DEMO-L202', 4, TIME '10:00'),
        ('DEMO-2B', 'DEMO-L201', 1, TIME '10:00'), ('DEMO-2B', 'DEMO-L201', 3, TIME '10:00'),
        ('DEMO-2C', 'DEMO-L203', 2, TIME '08:00'), ('DEMO-2C', 'DEMO-L203', 4, TIME '08:00'),
        ('DEMO-1C', 'DEMO-A103', 2, TIME '10:00'), ('DEMO-1C', 'DEMO-A103', 4, TIME '10:00'),
        ('DEMO-3A', 'DEMO-A102', 1, TIME '10:00'), ('DEMO-3A', 'DEMO-A102', 3, TIME '10:00'),
        ('DEMO-3B', 'DEMO-A104', 2, TIME '13:00'), ('DEMO-3B', 'DEMO-A104', 4, TIME '13:00'),
        ('DEMO-ADM1A', 'DEMO-A102', 1, TIME '08:00'), ('DEMO-ADM1A', 'DEMO-A102', 3, TIME '08:00'),
        ('DEMO-CON1A', 'DEMO-A101', 2, TIME '08:00'), ('DEMO-CON1A', 'DEMO-A101', 4, TIME '08:00'),
        ('DEMO-ENF1A', 'DEMO-S101', 1, TIME '13:00'), ('DEMO-ENF1A', 'DEMO-S101', 3, TIME '13:00'),
        ('DEMO-ETI1A', 'DEMO-A103', 5, TIME '08:00'), ('DEMO-ETI1A', 'DEMO-A103', 5, TIME '10:00')
    ) AS demo(group_code, room_code, day, start_time)
    JOIN grupo ON grupo.nombre = demo.group_code
    JOIN asignatura ON asignatura.id = grupo.subject_id
    JOIN teacher ON teacher.id = grupo.teacher_id
    JOIN classroom ON classroom.codigo = demo.room_code
    CROSS JOIN (SELECT min(id) AS id FROM schedule_version WHERE name = 'Demo · Semana académica') version
    WHERE classroom.state = 'DISPONIBLE' AND classroom.capacidad >= grupo.capacity AND teacher.active
      AND asignatura.duration_minutes > 0
      AND demo.start_time + make_interval(mins => asignatura.duration_minutes) > demo.start_time
      AND EXISTS (SELECT 1 FROM availability a WHERE a.teacher_id = teacher.id AND a.weekday = demo.day
          AND a.start_time <= demo.start_time AND a.end_time >= demo.start_time + make_interval(mins => asignatura.duration_minutes))
      AND NOT EXISTS (SELECT 1 FROM schedule_assignment a WHERE a.version_id = version.id AND a.day_of_week = demo.day
          AND a.start_time < demo.start_time + make_interval(mins => asignatura.duration_minutes) AND a.end_time > demo.start_time
          AND (a.group_id = grupo.id OR a.teacher_id = teacher.id OR a.classroom_id = classroom.id))
    ON CONFLICT DO NOTHING;
END;
$seed$;
