# Despliegue

Este código vive en dos lugares distintos, y es fácil confundirlos:

- **Acá** (`migration/backend` en el repo `jventas`) es donde se desarrolla. Historial completo, worktree compartido con el resto del proyecto original.
- **`https://github.com/ferd9/jventas-backend`** es una copia estandalone, con su propio historial limpio (sin el proyecto Swing original de 2021 que arrastra este repo), pensada solo para desplegar. Un GitHub Action ahí dispara el deploy a Cloud Run en cada push a `main`.

## Cómo publicar un cambio

1. Comitear acá primero (`git add backend/... && git commit`) — el paso siguiente exporta el último *commit*, no el working tree.
2. Exportar el snapshot limpio, **desde adentro de esta carpeta** (`D:/fun/jventas-backend/backend`, no desde el nivel de arriba):
   ```bash
   git archive migration/backend | tar -x -C /ruta/a/jventas-backend-repo
   ```
   Si se corre desde el nivel de arriba (`D:/fun/jventas-backend`), `git archive` deja de quedar scopeado a esta subcarpeta y vuelca el árbol completo del worktree — incluyendo los archivos del proyecto Swing original de 2021 (`Desert.jpg`, `Thumbs.db`, `nbproject/`, `.java` viejos) mezclados dentro de `src/`. Si pasa, todo lo que agrega queda sin trackear en el repo de destino (`git status --porcelain` muestra `??`) y se puede borrar sin riesgo antes de reintentar bien.
3. En `jventas-backend-repo`: `git add -A`, commit, `git push origin main` — dispara el deploy automático.

## Infraestructura (GCP)

Proyecto `jventas-app` (número `327145777303`), región `us-central1`.

- **Cloud Run**: servicio `jventas-backend`. Conecta a Cloud SQL vía el conector oficial (`com.google.cloud.sql:postgres-socket-factory` en el `pom.xml`, no el mount de socket Unix) — `SPRING_DATASOURCE_URL=jdbc:postgresql:///jventas?cloudSqlInstance=jventas-app:us-central1:jventas-db&socketFactory=com.google.cloud.sql.postgres.SocketFactory`.
- **Cloud SQL**: instancia `jventas-db` (Postgres 16, edición `ENTERPRISE` explícita — la edición por defecto `ENTERPRISE_PLUS` rechaza los tiers baratos tipo `db-f1-micro`). **Es el único componente que cobra las 24 horas** — todo lo demás escala a cero o es capa gratis. Para no gastar cuando no se usa: `gcloud sql instances patch jventas-db --activation-policy=NEVER` (se reinicia solo a los 7 días si no se repite).
- **Cloud Storage**: bucket `jventas-app-uploads`, montado en `/uploads` (`STORAGE_UPLOAD_DIR`) — así las imágenes de producto sobreviven a un redeploy.
- **Secret Manager**: `jwt-secret`, `db-password`.
- **CI/CD**: autenticación sin claves vía Workload Identity Federation (pool `github-actions-pool`, cuenta de servicio `github-actions-deployer@jventas-app.iam.gserviceaccount.com`).

## Gotchas de `gcloud` en Windows encontrados armando esto

- `gcloud` es en realidad `gcloud.cmd` — comas sin comillas en un valor de flag (`--set-env-vars`, `--set-secrets`, `--add-volume`) se tratan como separadores de argumento. Encerrar siempre esos valores entre comillas.
- Un build de Cloud Build atascado en `QUEUED` puede bloquear los siguientes (consume el cupo de builds concurrentes) — revisar **Cloud Build → Historial** y cancelar los que queden colgados si un deploy nuevo no arranca.
- Buildpacks no siempre respeta `<java.version>` del `pom.xml` — si el build falla con errores de Lombok (`cannot find symbol` en getters/setters generados), forzar la versión con `--set-build-env-vars=GOOGLE_RUNTIME_VERSION=21`.
