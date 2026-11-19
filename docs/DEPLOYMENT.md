# Deploying CabZen

CabZen is one Spring Boot JAR that serves both the API and the web pages, so it needs a single
Java 21 web service plus the Neon database.

## Recommended: Railway (no Docker needed)

Railway detects the Maven wrapper and builds the JAR itself.

1. Push the repository to GitHub.
2. Railway → **New Project** → **Deploy from GitHub repo** → choose the repository.
3. **Settings → Region:** pick Southeast Asia (Singapore), the same region as the Neon database
   (`aws-ap-southeast-1`). Every request makes several database calls, so keeping the app next to the database matters.
4. **Variables:** add these.

   | Variable | Value |
   |---|---|
   | `DATABASE_URL_UNPOOLED` | Neon **direct** connection string (host without `-pooler`) |
   | `ADMIN_EMAIL` | admin login email |
   | `ADMIN_PASSWORD` | a strong password (8+ characters) |
   | `SERVER_SERVLET_SESSION_COOKIE_SECURE` | `true`: the session cookie is only sent over HTTPS |

   Railway sets `PORT` itself and the app reads it (`server.port: ${PORT:8080}`).
   If the build picks a JDK other than 21, also set `RAILPACK_JDK_VERSION=21`.
5. **Settings → Networking → Generate Domain.** Railway serves it over HTTPS.
   Browser geolocation only works on HTTPS (or localhost), so use this URL for demos.
6. Deploy. The first start runs the Flyway migrations and creates the admin account.

Railway's default build skips tests (`-DskipTests`). That's intended: the tests need the database
and run locally with `./mvnw verify` before you push.

## Checking the deployment

- `https://<your-domain>/` shows the landing page.
- Log in with `ADMIN_EMAIL` and open the admin dashboard.
- The Railway logs show `Schema "public" is up to date` and `Started CabZenApplication`.

## Other hosts

Any host that can run `java -jar target/cabzen-0.0.1-SNAPSHOT.jar` with Java 21 works the same way:
build with `./mvnw -DskipTests package` and set the variables above.
Render, for example, needs a Dockerfile for Java apps. Docker is not part of the agreed stack, so agree on it before adding one.

## Notes

- **Secrets:** never put the database URL or admin password in `application.yml` or commit them.
  They live only in the host's variables (production) and `.env.local` (your machine).
- **Neon scale-to-zero:** after ~5 idle minutes the database pauses, and the first request afterwards takes a
  moment longer. The connection pool settings in `application.yml` already handle the dropped connections.
- **Neon branches:** for a clean production database, create a separate branch (for example `dev`) for local
  development and tests, and point production at `production`.
