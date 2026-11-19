# Aula Equilibrio

Aplicación JavaFX con persistencia en PostgreSQL mediante Hibernate. La aplicación se abre en pantalla completa con el **Dashboard** visible dentro del escritorio MDI. Pulsa **Esc** para volver al modo ventana o usa **Inicio → Salir de Aula Equilibrio** para cerrar la aplicación. La barra de tareas superior abre Horarios y los catálogos, marca los módulos abiertos y resalta el que está al frente.

Cada módulo se abre maximizado dentro del área de trabajo. Usa **Minimizar** para ocultarlo sin cerrar sus datos y vuelve a seleccionarlo desde la barra. Usa **Restaurar** para mover la ventana desde su barra de título y cambiar su tamaño desde la esquina inferior derecha.

También puedes arrastrar una ventana directamente desde la barra de título. Al acercarla a un lateral aparece una vista previa de media pantalla. En una esquina ocupa un cuarto del escritorio y en el borde superior se maximiza. Suelta para aplicar el ajuste o arrástrala de nuevo para recuperar su tamaño libre.

Pulsa la entrada de una ventana activa en la barra superior para minimizarla. Si está minimizada o detrás de otra, al pulsarla vuelve al frente. La entrada **Aula Equilibrio** corresponde al Dashboard. Para ver varias ventanas a la vez, elige **Inicio → Organizar en cascada**. Se acomodan superpuestas con sus barras de título visibles.

Pulsa una ventana interna para traerla al frente. Los avisos, las confirmaciones y los formularios de aulas y horarios se muestran dentro del escritorio. Resuelve el cuadro activo para continuar con los módulos.

## Dashboard institucional

El homepage presenta facultades y carreras, asignaturas únicas, grupos, docentes activos, aulas disponibles y sus plazas. La tabla por facultad muestra sus carreras, asignaturas, grupos, aulas utilizadas y sesiones asignadas. El ranking compara las cinco facultades que utilizan más aulas distintas; los pendientes permiten abrir el módulo correspondiente para revisar grupos sin profesor o sin horario, docentes activos sin disponibilidad y otros datos incompletos.

Selecciona una **versión de horario** para consultar uso de aulas y cobertura. Al iniciar se elige la más reciente por fecha e identificador; las alternativas se analizan por separado. Las consultas se ejecutan en segundo plano mediante `DashboardController → DashboardService → DashboardDAO`. Pulsa **Actualizar** o vuelve al dashboard desde otro módulo para refrescarlo. Si falla la conexión, se conserva la última lectura con un aviso y una opción de reintento.

Los indicadores tienen estas reglas:

- Las asignaturas se cuentan una sola vez dentro de una facultad, aunque varias de sus carreras las incluyan. También se incluyen las optativas.
- El modelo vincula las facultades a los horarios mediante las asignaturas de sus planes. Una asignatura compartida atribuye sus grupos y uso de aulas a cada facultad relacionada: esas filas no se suman para obtener el total institucional ni identifican un propietario exclusivo del aula.
- Un aula utilizada se cuenta una vez por facultad y versión, aunque tenga varias sesiones. El ranking mide cantidad de aulas distintas, no porcentaje de ocupación ni horas.
- La cobertura compara sesiones asignadas con las sesiones semanales requeridas por cada grupo con asignatura. Cada grupo aporta como máximo el 100% de sus sesiones requeridas. Los grupos antiguos sin asignatura quedan fuera de este cálculo. Sin versión o sin sesiones requeridas se muestra **—**.
- La cobertura no comprueba cruces ni reemplaza la validación de horarios. Un aula **disponible** está habilitada según su estado; puede estar ocupada en determinados intervalos. Los catálogos y el resumen por facultad incluyen registros activos e inactivos, mientras que la tarjeta de docentes muestra los activos.

Las gráficas de pastel comparan el estado de todas las aulas y las sesiones cubiertas/pendientes de la versión seleccionada. La gráfica de barras cuenta sesiones por día, no horas. Al pasar el cursor se ve el valor de cada dato; al pulsar se abre el módulo de Aulas o de Horarios. Si no existen datos se muestra un estado vacío, sin inventar porcentajes.

## Crear y revisar horarios

Abre **Horarios** y crea una versión o duplica una existente para probar una alternativa sin cambiar la original. El selector conserva cada versión por separado. Las lecturas y escrituras se ejecutan en segundo plano y las confirmaciones permanecen dentro del MDI.

1. Selecciona un grupo en el editor lateral o en la lista de grupos. Su asignatura y profesor se completan cuando están vinculados en Oferta académica. Un docente inactivo debe corregirse en ese módulo antes de guardar.
2. Pulsa una hora de la cuadrícula semanal para preparar el día y la hora de inicio. Elige una de las aulas disponibles, con capacidad suficiente y libre durante todo el intervalo. El editor explica los cruces de grupo o docente antes de habilitar el guardado.
3. Pulsa **Agregar sesión**. Para editar, pulsa la tarjeta de una clase o selecciónala en la vista **Lista**; cambia los datos y pulsa **Aplicar cambios**. Arrastra una tarjeta a otra hora o día para moverla conservando sus recursos. El destino indica si existe un cruce en los datos cargados.
4. Combina los filtros por grupo, docente o aula con la búsqueda. Estos filtros afectan la visualización; la comprobación de cruces siempre usa todas las sesiones de la versión. Activa **Sáb / dom** para ampliar la cuadrícula; si ya hay sesiones de fin de semana se muestran automáticamente. La lista lateral muestra sesiones programadas frente a las requeridas y coloca primero los grupos pendientes.
5. Usa **Duplicar** para crear una alternativa independiente. Eliminar una sesión de la copia conserva la versión original. Una versión con sesiones no se puede eliminar hasta retirarlas.

La universidad está abierta de **07:00 a 21:00**. La disponibilidad y las clases deben quedar dentro de ese rango; pueden terminar exactamente a las 21:00. La cuadrícula muestra toda la jornada. Cada sesión tiene un día y horas de inicio y fin en formato HH:mm. Su duración debe coincidir con los minutos de la asignatura. Los intervalos que se tocan (08:00–09:00 y 09:00–10:00) son compatibles; cualquier solapamiento parcial también se comprueba. La sesión debe quedar cubierta por la disponibilidad del docente, incluyendo la unión de intervalos contiguos. La cuadrícula representa la duración y separa las sesiones simultáneas en carriles. Pulsar o arrastrar usa pasos de 15 minutos; el editor admite cualquier minuto exacto.

Antes de cada escritura, una transacción vuelve a validar los intervalos, la disponibilidad, los cruces de grupo/docente/aula, la correspondencia con la asignatura/profesor y la capacidad/estado del aula. Un bloqueo de la versión serializa las escrituras simultáneas; los índices únicos protegen los inicios repetidos de un recurso. Un error revierte la operación. Tampoco se permite quitar disponibilidad necesaria para una sesión ya asignada ni cambiar la duración de una asignatura con horarios existentes.

Al iniciar se ejecuta `sql/migrate_time_intervals.sql`, que elimina las antiguas columnas numéricas de disponibilidad y asignaciones. Se recuperan horarios anteriores únicamente cuando hay una disponibilidad inequívoca y cabe la duración de la asignatura. Los registros sin equivalencia segura o con un cruce conservan sus relaciones y quedan pendientes de revisión, sin horas inventadas. Aparecen en **Lista** y en los pendientes del dashboard, y no cuentan como cobertura. Selecciónalos para asignarles horas o eliminarlos. La migración se puede repetir y no borra docentes, grupos, aulas ni versiones.

## Reportes

Abre **Reportes** desde la barra superior.

- **Horario** lista las sesiones de una versión y permite filtrarlas por grupo, docente o aula.
- **Carga docente** compara las horas asignadas con la disponibilidad semanal del docente. Cuando no hay disponibilidad registrada, el uso aparece como **—**.
- **Ocupación de aulas** muestra las sesiones y horas reservadas de cada aula por versión y día o semana. El porcentaje usa una jornada de **07:00 a 21:00** de lunes a domingo. Para aulas en mantenimiento o inactivas se muestra **—**.

Cada pestaña exporta su selección con JasperReports a **PDF** o **Excel (.xlsx)**. Los diseños `.jrxml` se editan con Jaspersoft Studio 7.0.x; consulta [docs/REPORTES.md](docs/REPORTES.md) para crear o modificar un reporte.

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

La semilla incluye 3 facultades (Ingeniería, Negocios y Ciencias de la Salud), 5 carreras y 10 asignaturas. Los planes contienen asignaturas obligatorias y optativas, algunas compartidas entre carreras y facultades.

Al abrir la aplicación, Hibernate prepara las tablas y ejecuta `sql/demo_seed.sql` en la base configurada. En una base nueva aparecen 8 docentes con 24 intervalos de disponibilidad, 12 grupos, 8 aulas de 3 tipos y un horario semanal con 24 asignaciones, sin cruces de grupo, docente ni aula. Cada grupo tiene dos sesiones. Los datos de ejemplo permiten explorar las gráficas y comparar el uso de aulas por facultad.

La semilla agrega filas de ejemplo que falten y conserva los registros existentes. Abre **Horarios** desde la barra superior y selecciona **Demo · Semana académica** para probar el filtro por grupo.

En **Oferta académica**, selecciona una asignatura para ver sus grupos. El formulario de asignatura define las horas por sesión, las sesiones semanales y las carreras donde es obligatoria u opcional. Puedes buscar por código o nombre y filtrar por carrera.

Cada grupo pertenece a una asignatura y permite definir código, cupo, demanda estimada y profesor. El profesor puede quedar pendiente. Para un grupo con horario, primero ajusta sus asignaciones antes de cambiar el profesor. Los registros anteriores conservan sus datos. La semilla vincula los grupos de demostración con sus asignaturas.

En **Aulas**, crea primero un tipo con nombre y descripción en el panel lateral y luego registra un aula con código, capacidad, tipo y estado (disponible, mantenimiento o inactiva). Selecciona una fila de **Tipos de aula** para filtrar la tabla principal o pulsa **Todos los tipos** para quitar ese filtro. La búsqueda por código/tipo y el filtro de estado se pueden combinar.

Los formularios y confirmaciones permanecen dentro del escritorio MDI. Los catálogos se cargan y guardan en segundo plano. No se puede borrar un tipo con aulas, reducir la capacidad por debajo del cupo de un grupo asignado ni inhabilitar un aula que conserva asignaciones. En **Horarios**, solo se pueden guardar asignaciones en aulas disponibles con capacidad suficiente.

En **Facultades y carreras**, selecciona una facultad para ver sus carreras. Cada tabla permite agregar, editar o eliminar registros. Para borrar una facultad, primero elimina o reasigna las carreras que la referencian.

En **Docentes y disponibilidad**, pulsa **Nuevo docente** o selecciona una fila y pulsa **Editar**. El formulario se abre en una ventana interna con su propia entrada en la barra de tareas. Al seleccionar un docente, el panel lateral muestra sus intervalos. El formulario también permite marcar al docente como activo o inactivo.

Busca docentes por nombre o correo y filtra por presencia de correo. En el panel lateral puedes filtrar por día. Si el filtro de docentes oculta la fila seleccionada, el panel lateral se limpia hasta que selecciones otro docente.

- El nombre es obligatorio y admite hasta 120 caracteres. El correo es opcional y único.
- Cada intervalo requiere un docente, un día y dos horas en formato HH:mm. La hora de inicio debe ser anterior a la de fin. Los registros anteriores sin horas se muestran como pendientes de definir y se completan al editarlos.
- Usa **Nuevo intervalo** para preparar un alta. Para editar o eliminar, selecciona una fila en el panel lateral.
- Un docente con intervalos asociados no se puede eliminar. La pantalla explica que primero deben eliminarse o reasignarse sus intervalos. La clave foránea también protege la relación en la base.

La consulta está en `CatalogDAO.listAvailability`:

```hql
select a from Availability a join fetch a.teacher t
where t.id = :teacherId
order by a.day, a.startTime nulls last, a.id
```

Cada operación abre una sesión, confirma su transacción y revierte los cambios ante un error. Las consultas se ejecutan en segundo plano para mantener la ventana operativa; los controles se habilitan al terminar.

## Revisión con dos docentes

Puedes crearlos desde la pantalla o ejecutar `sql/rodolfo_demo.sql` en tu base PostgreSQL después del primer inicio. El script agrega a Ana Pérez con lunes de 08:00 a 10:00 y miércoles de 13:00 a 15:00, y a Luis López con martes de 10:00 a 12:00; puede repetirse sin duplicar esos ejemplos.

1. Pulsa **Actualizar** y selecciona a Ana en la tabla principal. Deben aparecer únicamente sus dos intervalos.
2. Selecciona su intervalo del lunes, cambia el día a viernes y escribe un intervalo de 08:00 a 10:00; guarda y comprueba el resultado.
3. Selecciona a Ana e intenta eliminarla: debe aparecer el mensaje de intervalos asociados.
4. Elimina uno de sus intervalos y comprueba que desaparezca. Consulta a Luis y verifica que su intervalo siga intacto.
5. Cierra y abre la aplicación para comprobar la persistencia en PostgreSQL.

## Pruebas

```bash
./mvnw test
```

Cinco pruebas de integración Hibernate usan una base H2 temporal en modo PostgreSQL: CRUD, filtrado entre dos docentes, edición y eliminación de intervalos, validaciones, reasignación y protección de claves foráneas. No necesitan tu `.env` ni alteran tu base de trabajo.

Una prueba adicional carga el FXML y opera los controles JavaFX para comprobar el recorrido de revisión y el mensaje al borrar un docente referenciado. Requiere un servidor gráfico (`DISPLAY`); sin él se omite esa prueba. Las pruebas H2 no sustituyen la comprobación final con tu instalación PostgreSQL.

Las pruebas del dashboard añaden tres casos de integración ORM: base vacía, asignaturas compartidas sin duplicados y separación de versiones, y conteos de pendientes/capacidad. Otra prueba JavaFX carga el homepage completo, cambia de versión, refresca datos y verifica la recuperación después de un error de conexión simulado.

Las pruebas de Horarios comprueban copia independiente, edición, solapamientos parciales, intervalos contiguos, disponibilidad y dos guardados simultáneos. La prueba JavaFX opera la cuadrícula, el autocompletado, la vista de lista, los filtros y la duplicación/eliminación desde el editor. GitHub Actions ejecuta `verify` con JDK 21 y una pantalla virtual en cada PR a `develop`/`main` y después de integrar en esas ramas. Las pruebas no necesitan credenciales de PostgreSQL.

También se corrigieron el nombre de clase y el paquete de `Classroom.java`, que en la base original impedían compilar.
