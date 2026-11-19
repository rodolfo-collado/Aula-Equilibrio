# Reportes con JasperReports

La ventana **Reportes** (barra superior) tiene una pestaña por reporte. Cada pestaña muestra una vista previa con filtros en JavaFX y genera el documento final con JasperReports en **PDF** o **Excel (.xlsx)**. Después de guardar, el archivo se abre con la aplicación predeterminada del sistema.

En **Ocupación de aulas**, selecciona una versión y elige un día o **Semana completa**. El reporte muestra todas las aulas, incluidas las que tienen cero sesiones. El porcentaje divide las horas reservadas entre 14 horas para un día o 98 para la semana completa. Las aulas en mantenimiento o inactivas muestran **—**. Solo se cuentan las sesiones con intervalos definidos.

## Herramientas

- **Jaspersoft Studio 7.0.x** (Community). El proyecto usa JasperReports **7.0.3**; los `.jrxml` de la versión 6 tienen otro formato y no son compatibles.
- Fuente **DejaVu Sans**. Viene en `jasperreports-fonts` y Jaspersoft Studio ya la incluye; así el PDF se ve igual en todas las computadoras.

## Dónde está cada parte

| Parte | Ubicación |
| --- | --- |
| Diseños `.jrxml` | `src/main/resources/com/practice/aulaequilibrio/reports/` |
| Plantilla con estilos, encabezado y paginación | `reports/plantilla-base.jrxml` |
| Compilar, llenar y exportar | `report/ReportEngine` |
| Diálogo para guardar y generar en segundo plano | `util/ReportActions.export(...)` |
| Pestañas | `views/report-view.fxml` incluye `report-*.fxml` |
| Pruebas de los diseños | `src/test/.../report/ReportDesignsTest` |

## Crear un reporte nuevo

1. En Jaspersoft Studio abre `plantilla-base.jrxml` y usa **File → Save As** con el nombre del reporte (por ejemplo `ocupacion-aulas.jrxml`). Cambia también el atributo `name` del reporte.
2. Declara los **fields** del reporte. El nombre de cada field debe coincidir con la clave del `Map` que envía el servicio. Usa `java.lang.String`, `java.lang.Integer` o `java.lang.Double`; los números deben ser números, no texto, para que Excel pueda sumarlos.
3. Usa los estilos de la plantilla: `Titulo`, `Subtitulo`, `Encabezado` para los títulos de columna y `Celda` para los datos. No cambies la fuente.
4. Los parámetros comunes son `TITULO` y `SUBTITULO`. Pon el encabezado de columnas en `Column Header` (o en un grupo) y las filas en `Detail`.
5. El `.jrxml` **no lleva SQL**: los datos llegan desde Java mediante `JRMapCollectionDataSource`. La consulta va en el DAO (HQL) y el servicio transforma las filas:

   ```java
   public static List<Map<String, ?>> fields(List<MiFila> rows) {
       return rows.stream().<Map<String, ?>>map(row -> Map.of(
               "aula", row.code(),
               "horas", row.minutes() / 60.0)).toList();
   }
   ```

   Si un valor puede ser `null`, usa un `HashMap`, porque `Map.of` no acepta nulos. En el diseño marca **Blank When Null** o muestra «—».
6. En el controlador de la pestaña llama a:

   ```java
   ReportActions.export(tabla, "/com/practice/aulaequilibrio/reports/ocupacion-aulas.jrxml",
           Map.of("TITULO", "Ocupación de aulas", "SUBTITULO", "Versión: " + version),
           ScheduleReportService.roomOccupancyFields(List.copyOf(tabla.getItems()), dia),
           Format.PDF, "ocupacion aulas");
   ```

7. Agrega una prueba en `ReportDesignsTest` que llene el diseño con dos o tres filas y compruebe los textos con `ReportEngineTest.texts(print)`.

## Vista previa en Jaspersoft Studio

Para previsualizar sin base de datos, crea un **Data Adapter** de tipo *CSV* con columnas iguales a los fields. No subas ese adaptador ni los archivos `.jasper` compilados: la aplicación compila el `.jrxml` al usarlo por primera vez.

## Reglas

- Si no hay versión o no hay datos, el reporte muestra «No hay datos para esta selección.» No inventes porcentajes.
- Usa orientación horizontal A4 (842 × 595) para que quepan las tablas. El ancho útil es de 802 px.
- Cada reporte nuevo necesita un `.jrxml`, un método `fields(...)` en su servicio, la pestaña y su prueba.
