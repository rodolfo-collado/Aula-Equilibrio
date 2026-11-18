-- Ejecutar después del primer inicio. Agrega ejemplos sin reemplazar datos.
BEGIN;
INSERT INTO teacher (name, email)
VALUES ('Ana Pérez', 'ana.rodolfo@example.test'), ('Luis López', 'luis.rodolfo@example.test')
ON CONFLICT (email) DO NOTHING;
INSERT INTO availability (teacher_id, weekday, start_time, end_time)
SELECT t.id, example.day, example.start_time, example.end_time
FROM (VALUES
    ('ana.rodolfo@example.test', 1, TIME '08:00', TIME '10:00'),
    ('ana.rodolfo@example.test', 3, TIME '13:00', TIME '15:00'),
    ('luis.rodolfo@example.test', 2, TIME '10:00', TIME '12:00')
) AS example(email, day, start_time, end_time)
JOIN teacher t ON t.email = example.email
WHERE NOT EXISTS (SELECT 1 FROM availability a WHERE a.teacher_id = t.id AND a.weekday = example.day
    AND a.start_time < example.end_time AND a.end_time > example.start_time);
COMMIT;
