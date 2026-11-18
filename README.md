# Aula Equilibrio

Aplicación JavaFX con persistencia en PostgreSQL mediante Hibernate. La aplicación se abre en pantalla completa con el **Dashboard** visible dentro del escritorio MDI. Pulsa **Esc** para volver al modo ventana o usa **Inicio → Salir de Aula Equilibrio** para cerrar la aplicación. La barra de tareas superior abre Horarios y los catálogos, marca los módulos abiertos y resalta el que está al frente.

Cada módulo se abre maximizado dentro del área de trabajo. Usa **Minimizar** para ocultarlo sin cerrar sus datos y vuelve a seleccionarlo desde la barra. Usa **Restaurar** para mover la ventana desde su barra de título y cambiar su tamaño desde la esquina inferior derecha.

También puedes arrastrar una ventana directamente desde la barra de título. Al acercarla a un lateral aparece una vista previa de media pantalla. En una esquina ocupa un cuarto del escritorio y en el borde superior se maximiza. Suelta para aplicar el ajuste o arrástrala de nuevo para recuperar su tamaño libre.

Pulsa la entrada de una ventana activa en la barra superior para minimizarla. Si está minimizada o detrás de otra, al pulsarla vuelve al frente. La entrada **Aula Equilibrio** corresponde al Dashboard. Para ver varias ventanas a la vez, elige **Inicio → Organizar en cascada**. Se acomodan superpuestas con sus barras de título visibles.

Pulsa una ventana interna para traerla al frente. Los avisos, las confirmaciones y los formularios de aulas y horarios se muestran dentro del escritorio. Resuelve el cuadro activo para continuar con los módulos.

## Ejecutar

Hibernate registra las entidades en `src/main/resources/hibernate.cfg.xml`. La aplicación usa un solo `SessionFactory` y obtiene la conexión de `.env` o de las variables de entorno.

Los modelos y las proyecciones de consulta están en `model`. Los DAO concentran las consultas y transacciones de Hibernate. Los módulos de JavaFX llaman a `service`, que expone las operaciones del dominio y delega la persistencia. Los modelos que se gestionan juntos comparten servicio, como docentes y disponibilidad, aulas y tipos, o facultades, carreras y planes de estudio.

Se requiere JDK 21 y PostgreSQL. Copia `.env.example` a `.env` y configura `DB_URL`, `DB_USER` y `DB_PASSWORD` para una base existente. El usuario de la base debe poder crear/actualizar tablas, como requiere la configuración Hibernate existente.

Importa el `pom.xml` como proyecto Maven en IntelliJ IDEA. Maven configura el procesador de anotaciones de Lombok para generar los métodos y constructores al compilar.

Si tu base ya contiene aulas de una versión anterior, ejecuta primero `sql/rolando_aulas.sql` en esa base. El script asigna el tipo **General** a las aulas existentes que todavía no tienen tipo y conserva sus datos.

```bash
./mvnw javafx:run
```

## Datos de demostración

La semilla también incluye la Facultad de Ingeniería, la carrera Ingeniería en Sistemas y tres asignaturas en su plan de estudios.

Al abrir la aplicación, Hibernate prepara las tablas y ejecuta `sql/demo_seed.sql` en la base configurada. En una base nueva aparecen tres docentes, tres grupos, tres asignaturas, tres aulas y un horario con seis asignaciones.

La semilla agrega filas de ejemplo que falten y conserva los registros existentes. Abre **Horarios** desde la barra superior y selecciona **Demo · Semana académica** para probar el filtro por grupo.

En **Oferta académica**, selecciona una asignatura para ver sus grupos. El formulario de asignatura define las horas por sesión, las sesiones semanales y las carreras donde es obligatoria u opcional. Puedes buscar por código o nombre y filtrar por carrera.

Cada grupo pertenece a una asignatura y permite definir código, cupo, demanda estimada y profesor. El profesor puede quedar pendiente. Para un grupo con horario, primero ajusta sus asignaciones antes de cambiar el profesor. Los registros anteriores conservan sus datos. La semilla vincula los grupos de demostración con sus asignaturas.

En **Aulas**, crea primero un tipo con nombre y descripción en el panel lateral y luego registra un aula con código, capacidad, tipo y estado (disponible, mantenimiento o inactiva). Selecciona una fila de **Tipos de aula** para filtrar la tabla principal o pulsa **Todos los tipos** para quitar ese filtro. La búsqueda por código/tipo y el filtro de estado se pueden combinar.

Los formularios y confirmaciones permanecen dentro del escritorio MDI. Los catálogos se cargan y guardan en segundo plano. No se puede borrar un tipo con aulas, reducir la capacidad por debajo del cupo de un grupo asignado ni inhabilitar un aula que conserva asignaciones. En **Horarios**, solo se pueden guardar asignaciones en aulas disponibles con capacidad suficiente.

En **Facultades y carreras**, selecciona una facultad para ver sus carreras. Cada tabla permite agregar, editar o eliminar registros. Para borrar una facultad, primero elimina o reasigna las carreras que la referencian.

En **Docentes y disponibilidad**, pulsa **Nuevo docente** o selecciona una fila y pulsa **Editar**. El formulario se abre en una ventana interna con su propia entrada en la barra de tareas. Al seleccionar un docente, el panel lateral muestra sus intervalos. El formulario también permite marcar al docente como activo o inactivo.

Busca docentes por nombre o correo y filtra por presencia de correo. En el panel lateral puedes filtrar por día. Si el filtro de docentes oculta la fila seleccionada, el panel lateral se limpia hasta que selecciones otro docente.

- El nombre es obligatorio y admite hasta 120 caracteres. El correo es opcional y único.
- Cada intervalo requiere un docente, un día y dos horas en formato HH:mm. La hora de inicio debe ser anterior a la de fin. Los bloques antiguos siguen visibles por número y se convierten a intervalos al editarlos.
- Usa **Nuevo intervalo** para preparar un alta. Para editar o eliminar, selecciona una fila en el panel lateral.
- Un docente con bloques asociados no se puede eliminar. La pantalla explica que primero deben eliminarse o reasignarse sus bloques. La clave foránea también protege la relación en la base.

La consulta está en `CatalogDAO.listAvailability`:

```hql
select a from Availability a join fetch a.teacher t
where t.id = :teacherId
order by a.day, a.startTime nulls last, a.blockNumber, a.id
```

Cada operación abre una sesión, confirma su transacción y revierte los cambios ante un error. Las consultas se ejecutan en segundo plano para mantener la ventana operativa; los controles se habilitan al terminar.

## Revisión con dos docentes

Puedes crearlos desde la pantalla o ejecutar `sql/rodolfo_demo.sql` en tu base PostgreSQL después del primer inicio. El script agrega a Ana Pérez con lunes/bloque 2 y miércoles/bloque 4, y a Luis López con martes/bloque 6; puede repetirse sin duplicar esos ejemplos.

1. Pulsa **Actualizar** y selecciona a Ana en la tabla principal. Deben aparecer únicamente sus dos bloques.
2. Selecciona su bloque 2, cambia el día a viernes y escribe un intervalo de 08:00 a 10:00; guarda y comprueba el resultado.
3. Selecciona a Ana e intenta eliminarla: debe aparecer el mensaje de bloques asociados.
4. Elimina uno de sus bloques y comprueba que desaparezca. Consulta a Luis y verifica que su bloque siga intacto.
5. Cierra y abre la aplicación para comprobar la persistencia en PostgreSQL.

## Pruebas

```bash
./mvnw test
```

Cinco pruebas de integración Hibernate usan una base H2 temporal en modo PostgreSQL: CRUD, filtrado entre dos docentes, edición y eliminación de bloques, validaciones, reasignación y protección de claves foráneas. No necesitan tu `.env` ni alteran tu base de trabajo.

Una sexta prueba carga el FXML y opera los controles JavaFX para comprobar el recorrido de revisión y el mensaje al borrar un docente referenciado. Requiere un servidor gráfico (`DISPLAY`); sin él se omite esa prueba. Las pruebas H2 no sustituyen la comprobación final con tu instalación PostgreSQL.

También se corrigieron el nombre de clase y el paquete de `Classroom.java`, que en la base original impedían compilar.
