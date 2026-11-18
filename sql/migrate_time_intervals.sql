-- Transición de las versiones anteriores. Los registros sin equivalencia segura
-- conservan sus relaciones y quedan sin horas para revisión desde la aplicación.
DO $migration$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('aulaequilibrio_time_intervals'));
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'schedule_assignment' AND column_name = 'block_number') THEN
        UPDATE schedule_assignment assignment
        SET start_time = available.start_time,
            end_time = available.start_time + make_interval(mins => subject.duration_minutes)
        FROM (SELECT teacher_id, weekday, block_number, min(start_time) AS start_time, min(end_time) AS end_time
              FROM availability WHERE start_time IS NOT NULL AND end_time > start_time
              GROUP BY teacher_id, weekday, block_number
              HAVING count(DISTINCT start_time) = 1 AND count(DISTINCT end_time) = 1) available,
             asignatura subject
        WHERE assignment.teacher_id = available.teacher_id AND assignment.day_of_week = available.weekday
          AND assignment.block_number = available.block_number AND assignment.subject_id = subject.id
          AND subject.duration_minutes > 0
          AND available.start_time >= TIME '07:00'
          AND available.start_time + make_interval(mins => subject.duration_minutes) <= TIME '21:00'
          AND subject.duration_minutes <= extract(epoch FROM (available.end_time - available.start_time)) / 60
          AND available.start_time + make_interval(mins => subject.duration_minutes) <= available.end_time
          AND (assignment.start_time IS NULL OR assignment.end_time IS NULL);

        UPDATE schedule_assignment target SET start_time = NULL, end_time = NULL
        WHERE target.start_time IS NOT NULL AND EXISTS (
            SELECT 1 FROM schedule_assignment earlier
            WHERE earlier.id < target.id AND earlier.version_id = target.version_id
              AND earlier.day_of_week = target.day_of_week
              AND earlier.start_time < target.end_time AND earlier.end_time > target.start_time
              AND (earlier.group_id = target.group_id OR earlier.teacher_id = target.teacher_id OR earlier.classroom_id = target.classroom_id));
        ALTER TABLE schedule_assignment DROP COLUMN block_number;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'availability' AND column_name = 'block_number') THEN
        ALTER TABLE availability DROP COLUMN block_number;
    END IF;

    UPDATE availability SET start_time = NULL, end_time = NULL
    WHERE start_time < TIME '07:00' OR end_time > TIME '21:00' OR end_time <= start_time;
    UPDATE schedule_assignment SET start_time = NULL, end_time = NULL
    WHERE start_time < TIME '07:00' OR end_time > TIME '21:00' OR end_time <= start_time;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_schedule_hours' AND conrelid = 'schedule_assignment'::regclass) THEN
        ALTER TABLE schedule_assignment ADD CONSTRAINT ck_schedule_hours CHECK
            ((start_time IS NULL AND end_time IS NULL) OR (start_time >= TIME '07:00' AND end_time <= TIME '21:00'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_availability_hours' AND conrelid = 'availability'::regclass) THEN
        ALTER TABLE availability ADD CONSTRAINT ck_availability_hours CHECK
            ((start_time IS NULL AND end_time IS NULL) OR (start_time >= TIME '07:00' AND end_time <= TIME '21:00'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_schedule_interval'
                   AND conrelid = 'schedule_assignment'::regclass) THEN
        ALTER TABLE schedule_assignment ADD CONSTRAINT ck_schedule_interval CHECK (
            (start_time IS NULL AND end_time IS NULL) OR
            (start_time IS NOT NULL AND end_time IS NOT NULL AND start_time < end_time));
    END IF;
    CREATE UNIQUE INDEX IF NOT EXISTS uk_schedule_room_start ON schedule_assignment(version_id, day_of_week, start_time, classroom_id);
    CREATE UNIQUE INDEX IF NOT EXISTS uk_schedule_teacher_start ON schedule_assignment(version_id, day_of_week, start_time, teacher_id);
    CREATE UNIQUE INDEX IF NOT EXISTS uk_schedule_group_start ON schedule_assignment(version_id, day_of_week, start_time, group_id);
END;
$migration$;
