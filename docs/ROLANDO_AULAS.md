# Aulas y tipos de aula

## Preparar la base

Sigue la configuración de PostgreSQL del README principal. Si tu base ya contiene aulas, ejecuta `sql/rolando_aulas.sql` antes de iniciar. El script agrega el tipo **General** a las aulas anteriores que todavía no tienen tipo. Si existen capacidades menores o iguales a cero, corrígelas antes de repetir la migración.

## Probar desde JavaFX

1. Inicia con `./mvnw javafx:run` o `mvnw.cmd javafx:run` en Windows.
2. Abre **Aulas** y crea el tipo **Laboratorio** con su descripción desde el panel lateral.
3. En el panel principal, registra un aula con un código único, una capacidad positiva, el tipo **Laboratorio** y estado **Disponible**.
4. Selecciona **Laboratorio** en la tabla lateral para filtrar las aulas. Combina el filtro con una búsqueda y con el estado. Usa **Todos los tipos**, limpia la búsqueda y selecciona **Todos los estados** para recuperar todas las filas. Edita la capacidad y reinicia la aplicación para comprobar que el cambio se guardó.
5. Intenta eliminar **Laboratorio** mientras tenga un aula asociada. Reasigna o elimina el aula antes de borrar el tipo.
6. Cambia el aula a **Mantenimiento** e intenta asignarla desde **Horarios**: la operación debe rechazarse. Vuelve a marcarla **Disponible** y crea una asignación con un grupo cuyo cupo quepa en el aula.
7. Con esa asignación existente, intenta reducir la capacidad por debajo del cupo del grupo o cambiar el estado a **Inactiva**. Debe pedir que ajustes o retires primero las asignaciones. Borra la asignación, elimina el aula y finalmente elimina su tipo.

`ClassroomController` usa `ClassroomService`, que delega las transacciones en `ClassroomDAO` y `ClassroomTypeDAO`. Las consultas y escrituras se ejecutan fuera del hilo gráfico. Los formularios y avisos usan `Dialogs` dentro del MDI.

`ClassroomDAO` lista las aulas y sus tipos mediante HQL con `JOIN FETCH`. La clave foránea protege los tipos referenciados y el campo de versión detecta actualizaciones concurrentes. Al editar un aula se comprueba su uso en horarios antes de modificar estado o capacidad. Las aulas anteriores reciben el estado **Disponible** al actualizar el esquema; no se reemplazan sus datos.
