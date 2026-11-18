# Getting started with RideX

This guide is for developers who are new to RideX. Follow it top to bottom and in about 30 minutes you will
understand how the app works, have it running on your machine, have done a full ride yourself, and know how
to test your changes.

Contents: [1. What RideX is](#1-what-ridex-is) · [2. How the pieces fit](#2-how-the-pieces-fit) ·
[3. Install the tools](#3-install-the-tools) · [4. Set up the database](#4-set-up-the-database-one-time) ·
[5. Run the app](#5-run-the-app) · [6. Try a full ride](#6-try-a-full-ride) · [7. Run the tests](#7-run-the-tests) ·
[8. Find your way around the code](#8-find-your-way-around-the-code) · [9. House rules](#9-house-rules) ·
[10. When something goes wrong](#10-when-something-goes-wrong) · [11. Words used in this project](#11-words-used-in-this-project)

---

## 1. What RideX is

RideX is a bike-taxi booking website, like Rapido. It has three kinds of users:

| User | What they do |
|---|---|
| **Passenger** | Picks a pickup and a drop on a map, sees the fare, books a ride, follows the captain live, rates the ride. |
| **Captain** (called *driver* in the code) | Signs up, uploads licence and bike papers, goes online, accepts nearby rides, earns money. |
| **Admin** | Approves captains, blocks users, switches bikes on or off, watches every ride on a live map. |

Every ride moves through these steps:

```
REQUESTED ──captain accepts──► ACCEPTED ──passenger's OTP──► STARTED ──captain completes──► COMPLETED
    │                              │
    ├──── cancelled ───────────────┴──► CANCELLED
    └──── nobody accepts in 3 min ─────► EXPIRED
```

## 2. How the pieces fit

RideX is **one Spring Boot app**. It serves the web pages *and* the JSON API, so there is no separate frontend
server to run.

```
 Browser: plain HTML + Bootstrap + JavaScript + Leaflet map
     │   fetch() calls with JSON, logged in by a session cookie
     ▼
 Spring Boot app (Java 21), http://localhost:8080
     controller  →  service (the rules)  →  repository (database access)
     │                                          │
     ▼                                          ▼
 OSRM + Nominatim                         Neon PostgreSQL (cloud database)
 (road routes, address search)            Flyway creates the tables on startup
```

**One click, followed through the code.** When a passenger presses **Book ride**:

1. [passenger/home.js](../src/main/resources/static/passenger/home.js) `book()` calls `Api.post('/api/passenger/bookings', …)`.
2. [PassengerController](../src/main/java/com/ridex/controller/PassengerController.java) `book()` receives it.
3. [BookingService](../src/main/java/com/ridex/service/BookingService.java) `create()` checks the rules, gets the
   road route, works out the fare (the browser never sends a price), and saves the booking.
4. The answer goes back as JSON, and the page shows "Finding a captain near you…".
5. Every 4 seconds the page asks for the ride again, so it notices when a captain accepts.

Most features work this same way: **page `.js` → controller → service → repository**.

The full design (database diagram, every API endpoint, every page) is in [PLAN.md](PLAN.md).

## 3. Install the tools

| Tool | Why | Check it works |
|---|---|---|
| **Git** | get the code | `git --version` |
| **JDK 21** (e.g. [Microsoft Build of OpenJDK 21](https://learn.microsoft.com/java/openjdk/download)) | runs the app | `java -version` must say **21** |
| **A Neon account** ([neon.com](https://neon.com), free) | the database | you can log in to the console |
| **Two browsers**, e.g. Chrome and Edge | to be passenger and captain at the same time | — |

You do **not** need to install Maven, Node.js or Docker. The `mvnw` script in the project downloads the right Maven
version by itself the first time you run it.

Then get the code:

```bash
git clone https://github.com/Sahil-957/Ride_X.git
cd Ride_X
```

## 4. Set up the database (one time)

The app reads its database address and admin login from a file called **`.env.local`** in the project folder.
This file holds secrets, so it is never committed (it is in `.gitignore`).

1. **Get a database.** Either ask the project owner to invite you to the RideX Neon project, or create your own
   free Neon project. An empty database is fine: the app creates all tables itself on first start.
2. **Make your own branch.** In the Neon console open **Branches → New branch**, name it `dev-yourname`.
   A branch is your private copy of the database. The tests write and delete rows, so keep them away from the
   shared `production` branch.
3. **Copy the two connection strings.** On your branch click **Connect**. Copy the string once with
   *connection pooling* **on** and once with it **off**.
4. **Create `.env.local`** from the example file:

   ```bash
   cp .env.example .env.local                 # Windows PowerShell: Copy-Item .env.example .env.local
   ```

   Then fill it in:

   ```properties
   DATABASE_URL=postgresql://...-pooler.../neondb?sslmode=require    # pooling ON (not used by the app yet)
   DATABASE_URL_UNPOOLED=postgresql://.../neondb?sslmode=require     # pooling OFF, the one the app uses
   ADMIN_EMAIL=admin@ridex.local
   ADMIN_PASSWORD=choose-8-or-more-characters
   ```

There is no admin sign-up page. The admin account is created from `ADMIN_EMAIL` / `ADMIN_PASSWORD` when the app
starts. If you change the password here, restart the app and the new password works.

## 5. Run the app

Always run commands from the **project folder** (where `pom.xml` is), otherwise `.env.local` is not found.

```bash
./mvnw spring-boot:run          # Windows PowerShell: .\mvnw.cmd spring-boot:run
```

The first run downloads Maven and the libraries, which takes a few minutes. Later runs start in about 20 seconds.
You are ready when the log shows:

```
Started RideXApplication in ... seconds
```

Open **http://localhost:8080** and log in with your `ADMIN_EMAIL` / `ADMIN_PASSWORD`. You should land on the admin dashboard.

To stop the app press `Ctrl + C`. To use another port:

```bash
PORT=8081 ./mvnw spring-boot:run                     # Git Bash / macOS / Linux
$env:PORT=8081; .\mvnw.cmd spring-boot:run           # Windows PowerShell
```

## 6. Try a full ride

Each browser keeps one login, so give each user their own browser window:

| Window | Who |
|---|---|
| Chrome, normal window | Admin |
| Chrome, incognito window | Captain |
| Edge (or another browser) | Passenger |

**Captain signs up (incognito window)**
1. Open http://localhost:8080, click **Become a captain**, fill the form (any licence number, a 10-digit mobile starting 6–9).
2. On **Profile & documents**: save a licence expiry date in the future, save a bike (number like `MH12AB1234`),
   and upload three files (licence, RC, ID proof). Files must be **real PDF, JPG or PNG files, max 2 MB**.
   Any small photo or PDF works.

**Admin approves (Chrome window)**
3. Open **Captains**, click **Review** next to the new captain, open a document, click **Approve**.

**Captain goes online (incognito window)**
4. Open **Dashboard**. Allow location, or simply **tap the map** to place the captain (demo mode).
5. Switch **online**. Keep this tab open: it sends the captain's position every 15 seconds.

**Passenger books (Edge)**
6. Sign up as a passenger. Set the pickup **within 5 km of the captain** (tap the map, search an address, or use GPS),
   then set a drop. The fare and "1 captain nearby" appear. Click **Book ride**.

**The ride**
7. Captain: the request card appears within a few seconds. Click **Accept**.
8. Passenger: the captain's name, bike and a **4-digit OTP** appear.
9. Captain: type the OTP, click **Start ride**, then **Payment collected · Complete ride**.
10. Passenger: rate the ride. Admin: the dashboard now shows the ride and today's revenue.

Also worth trying: the captain clicks **Skip** or **Cancel this ride** (the ride goes to other captains), the
passenger cancels, or nobody accepts for 3 minutes (the ride expires).

## 7. Run the tests

**Quick tests: no database, a few seconds.** Good while you work:

```bash
./mvnw test -Dtest='FareServiceTest,NeonUrlTest,AdminSeederTest'
# Windows PowerShell: .\mvnw.cmd test "-Dtest=FareServiceTest,NeonUrlTest,AdminSeederTest"
```

**All tests: about 3 minutes.** Run this before you share any change:

```bash
./mvnw verify                   # Windows PowerShell: .\mvnw.cmd verify
```

It ends with `BUILD SUCCESS` when everything passes. Results per test class are in `target/surefire-reports/`.
To run one class: `./mvnw test -Dtest=RideFlowTest`.

Things to know about the full run:

- Tests that need a database use the one in your `.env.local`. That is why section 4 gives you your own branch.
  They delete exactly the rows they create.
- `RideFlowTest` plays a whole ride, including **two captains pressing Accept at the same moment** (only one may
  win). The README has [the list of what each test proves](../README.md#tests).
- New test classes must end in `Test` or `Tests`, otherwise Maven skips them.

**Check by hand too.** Tests cover the server rules. After changing a page, open it in the browser and check:
it loads without errors in the browser console (F12), shows a loading state, shows an empty state when there is
no data, and shows a readable message when something fails. Check it at phone width too (F12 → device toolbar).

## 8. Find your way around the code

```
src/main/java/com/ridex/
  controller/   receives web requests            e.g. PassengerController, DriverController, AdminController
  service/      the rules of the app              BookingService (ride lifecycle), DriverService, AdminService, FareService
  repository/   database queries                  BookingRepository, DriverProfileRepository, ...
  entity/       database tables as Java classes   Booking, User, DriverProfile, Vehicle, Payment, Rating, ...
  dto/          JSON shapes sent and received     Java records, e.g. BookingDtos.TripRequest
  security/     login, roles, CSRF                SecurityConfig
  config/       settings, database URL, admin     AppProperties, DatabaseConfig, AdminSeeder
  exception/    error handling                    ApiException, GlobalExceptionHandler
src/main/resources/
  application.yml           settings (fare, dispatch radius, ...)
  db/migration/             database tables (Flyway SQL files)
  static/                   the website: index, login, register + passenger/, driver/, admin/ pages
    js/api.js  js/ui.js  js/map.js    shared helpers used by every page
src/test/java/com/ridex/    tests
```

**"I want to change…"**

| I want to change… | Look here |
|---|---|
| fare numbers, commission, 5 km radius, 3-minute expiry | `ridex:` section of [application.yml](../src/main/resources/application.yml) |
| the fare formula | [FareService.java](../src/main/java/com/ridex/service/FareService.java) |
| what can happen to a ride (accept, start, cancel, …) | [BookingService.java](../src/main/java/com/ridex/service/BookingService.java) |
| captain onboarding, online/offline, earnings | [DriverService.java](../src/main/java/com/ridex/service/DriverService.java) |
| admin features | [AdminService.java](../src/main/java/com/ridex/service/AdminService.java) + `static/admin/` |
| who may open which page or API | [SecurityConfig.java](../src/main/java/com/ridex/security/SecurityConfig.java) |
| a page | `static/<role>/<page>.html` and the `.js` file next to it |
| how API calls, toasts or the navbar work | [js/api.js](../src/main/resources/static/js/api.js), [js/ui.js](../src/main/resources/static/js/ui.js) |
| the map, the default city (Mumbai) | [js/map.js](../src/main/resources/static/js/map.js) |
| routes and trip time | [OsrmRoutingService.java](../src/main/java/com/ridex/service/routing/OsrmRoutingService.java) |
| a database table or column | add a **new** file `db/migration/V2__what_changed.sql`, then update the entity |
| an error message the user sees | the `ApiException` thrown in the service |

**Adding a feature, step by step:** (1) if the database changes, add a migration; (2) add a DTO record for the JSON;
(3) put the rule in a service method; (4) expose it in a controller; (5) call it from the page with `Api.get/post`;
(6) add or extend a test; (7) run `./mvnw verify` and try it in the browser.

## 9. House rules

The short version of [CLAUDE.md](../CLAUDE.md), the project's working rules:

- **The stack is fixed:** Java 21, Spring Boot, Maven, Neon PostgreSQL, plain HTML/CSS/JS with Bootstrap and Leaflet.
  Ask the project owner before adding any new library or tool.
- **Never edit a migration that has already run.** Change the database with a new `V{n}__description.sql` file.
- **The server decides.** Prices, permissions and ride status are always checked on the server, never trusted from the page.
- **Errors are honest.** When something fails, throw `ApiException(HttpStatus.X, "message the user can act on")`.
- **Secrets stay out of git.** Database URLs and passwords live only in `.env.local`. Test data uses
  `example.com` or `ridex.local` emails.
- **Ask before deleting data** in the shared database (DELETE, DROP, resetting a branch).
- **Every page** shows a loading state, an empty state and an error state, and works on a phone.

## 10. When something goes wrong

| You see | What it means | Fix |
|---|---|---|
| `Could not resolve placeholder 'DATABASE_URL_UNPOOLED'` | `.env.local` was not found | Create it (section 4) and run from the project folder. |
| `Port 8080 was already in use` | another program uses the port | Stop it, or start with `PORT=8081` (section 5). |
| Build error about Java version / `release 21` | Maven is using an older Java | Install JDK 21 and point `JAVA_HOME` at it. |
| First page after a break is slow | the free Neon database was asleep | Normal. It wakes in a few seconds. |
| Admin login says "Invalid email or password" | `.env.local` values differ from what you typed | Check `ADMIN_EMAIL` / `ADMIN_PASSWORD`, then restart the app. |
| "No captains are available near your pickup" | no captain can take the ride | The captain must be approved, online, have an active bike, be within 5 km, and keep their dashboard tab open. Then click **Check again**. |
| Logging in as the captain logs the passenger out | both are in the same browser window | Use separate browsers or an incognito window (section 6). |
| Captain shows "Waiting for GPS…" | the browser blocked location | Tap the map to set a position. Location only works on `localhost` or HTTPS. |
| Map is grey | map tiles come from the internet | Check your internet connection. |
| "Address search is unavailable" | the free address service is busy | Tap the map instead, or try again in a minute. |
| Upload is rejected | the file is not a real PDF/JPG/PNG, or is over 2 MB | The server checks the file's content, not its name. Use a real file. |
| **Approve** fails | the captain is missing something | They need a valid licence expiry, a bike and all three documents. |
| `./mvnw verify` can't replace the `.jar` (Windows) | the app is still running from that jar | Stop the app first. |
| Tests fail with connection errors | the database in `.env.local` can't be reached | Check the URL, your internet, and that the Neon project is not suspended. |

## 11. Words used in this project

| Word | Meaning |
|---|---|
| **Captain / driver** | Same person. The screens say *captain*; the code says *driver* (`DriverService`, `/api/driver`, `driver/` pages). |
| **Booking / ride** | Same thing. One trip from pickup to drop. |
| **OTP** | 4-digit code the passenger tells the captain to start the ride, so the right person is picked up. |
| **Dispatch** | Sending a new ride request to all free captains within 5 km. The first one to accept gets it. |
| **Commission** | RideX keeps 20% of each paid fare; the captain earns the rest. |
| **Flyway migration** | A numbered SQL file that changes the database. They run once, in order, on startup. |
| **Neon branch** | A copy of the database you can use without touching anyone else's data. |
| **ProblemDetail** | The JSON shape of every error: `{status, title, detail, errors}`. |
| **CSRF token** | A security code `js/api.js` sends with every change, so other websites can't act as you. |

Next steps: read [PLAN.md](PLAN.md) for the full design and API list, and [DEPLOYMENT.md](DEPLOYMENT.md) to put RideX online.
