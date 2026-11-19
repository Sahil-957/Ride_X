# How CabZen works

This page explains, step by step, what each person does in CabZen and what the app does behind the scenes.
It needs no coding knowledge. To run the app, see [GETTING_STARTED.md](GETTING_STARTED.md). For the technical
design (database, API list), see [PLAN.md](PLAN.md).

## The three kinds of user

| User | What they do | Where they land after logging in |
|---|---|---|
| **Passenger** | Books a bike ride, watches the captain arrive, pays, rates the ride | Book a ride page |
| **Captain** (bike driver) | Gets approved, goes online, accepts rides, earns money | Dashboard, or *Profile & documents* until approved |
| **Admin** | Approves captains, blocks bad accounts, watches every ride | Admin dashboard |

Everyone uses the same **Log in** page; CabZen sends each person to their own home page. If someone opens a
page meant for another kind of user, they are sent back to their own home.

## A ride from start to finish

Before any ride can happen, a captain must be **approved** and **online** (see the next two sections).

| Step | Who | What they do | What CabZen does |
|---|---|---|---|
| 1 | Passenger | Sets a **pickup** and a **drop** by searching an address, tapping the map, using GPS, or dragging the pins | Finds the real road route and shows the distance, time, **fare** and how many captains are nearby |
| 2 | Passenger | Chooses **Cash** or **UPI** and taps **Book ride** | Works out the fare again on the server (the browser never sends a price), creates the ride with status **REQUESTED** and makes a secret 4-digit **OTP** |
| 3 | CabZen | | Shows the request to every free, online captain within **5 km** of the pickup |
| 4 | Captain | Sees the request card (pickup, drop, distance, fare, payment type) and taps **Accept**, or **Skip** | The first captain to accept gets the ride; status becomes **ACCEPTED**. A skipped ride is never shown to that captain again |
| 5 | Passenger | Sees the captain's name, rating, bike, number plate, a call button, the captain moving on the map, and the **OTP** | Updates the passenger's screen every 4 seconds |
| 6 | Captain | Meets the passenger, asks for the OTP, types it and taps **Start ride** | Checks the OTP; status becomes **STARTED** |
| 7 | Captain | Drops the passenger and taps **Payment collected · Complete ride** | Status becomes **COMPLETED**; the payment is marked paid and split: 80% to the captain, 20% to CabZen |
| 8 | Passenger | Gives the captain **1 to 5 stars** and an optional comment | Saves the rating (one per ride); it counts towards the captain's average |

The finished ride then appears in the passenger's **My rides**, in the captain's **Earnings**, and on the
admin dashboard (today's rides, revenue and commission).

### When a ride doesn't go to plan

| What happens | Result |
|---|---|
| Passenger taps **Cancel** before the ride starts | Ride is **CANCELLED**; the payment is voided. Once started, it can't be cancelled |
| Captain taps **Cancel this ride** after accepting, before starting | The ride goes back to **REQUESTED** for other captains, with a fresh 3 minutes. It is never offered to that captain again |
| No captain accepts within **3 minutes** | Ride becomes **EXPIRED**; the passenger can book again |
| No captain is near the pickup | Booking is refused with a message; the passenger can tap **Check again** |
| Captain types the wrong OTP | "Wrong OTP" message; the ride does not start |
| Passenger already has an open ride | A second booking is refused |
| Pickup and drop closer than 0.3 km, or more than 50 km apart | Booking is refused with a message |

### Ride statuses at a glance

```
              captain accepts          captain enters OTP        captain completes
REQUESTED ───────────────────▶ ACCEPTED ──────────────────▶ STARTED ─────────────────▶ COMPLETED
  │    ▲                          │
  │    └──── captain cancels ─────┤
  │                               │
  ├──── passenger cancels ────────┴─────▶ CANCELLED
  └──── nobody accepts in 3 minutes ────▶ EXPIRED
```

## Becoming a captain

1. **Sign up** with *Become a captain*: name, email, a 10-digit mobile number (starting 6–9), a password of
   8+ characters and a licence number.
2. On **Profile & documents**, add:
   - licence number and **expiry date**;
   - bike details: number plate (like `MH12AB1234`), make, model, colour, year;
   - three documents: **driving licence**, **RC** (vehicle registration) and **ID proof**. Each must be a real
     PDF, JPG or PNG file of at most 2 MB. CabZen checks the file's contents, not just its name.
3. The profile is now **PENDING**: the captain waits for the admin.
4. The admin either:
   - **approves** it. CabZen allows this only when the licence details are filled in and not expired, a bike is
     added and all three documents are uploaded. The captain can now use the Dashboard.
   - **rejects** it with a reason. The captain sees the reason, fixes the details, and saving them sends the
     profile back to **PENDING** for another review.
5. Once approved, the details are locked. To change them, the captain contacts the admin.

## A captain's working day

1. Open the **Dashboard** and allow location access. Without GPS, the captain can **tap the map** to set a
   position, which is handy for a demo.
2. Switch **online**. CabZen only allows this when the captain is approved, the account is not blocked and the
   bike is active.
3. While the page is open it sends the captain's position every **15 seconds** and checks for new requests every
   **4 seconds**.
4. Accept a ride, then follow steps 4–7 above. A captain can only have **one ride at a time**, and sees no new
   requests until it is finished.
5. **Earnings** shows money earned today, in the last 7 days and in the last 30 days, a daily chart, the average
   rating and the number of completed rides.
6. Switch **offline** to stop getting requests. A captain who closes the app without going offline is switched
   off automatically after 10 minutes without GPS.

## What the admin does

| Page | What the admin can do | What happens |
|---|---|---|
| **Dashboard** | See passengers, captains, captains online now, active rides, today's revenue and commission, a 14-day revenue chart, rides by status, and a live map of open rides and online captains | Refreshes every 10 seconds |
| **Captains** | **Review** a captain's details and documents, then **Approve** or **Reject** with a reason | See *Becoming a captain* |
| **Captains / Passengers** | **Block** or **unblock** an account | A blocked person is logged out at once. A blocked passenger can't book; a blocked captain is switched offline and can't go online. Admin accounts can't be blocked |
| **Vehicles** | **Deactivate** or reactivate a bike | The captain is switched offline and can't go online until the bike is active again |
| **Bookings** | Browse every ride with its full details | |

There is no admin sign-up page. The admin account comes from the `ADMIN_EMAIL` and `ADMIN_PASSWORD`
settings when the app starts.

## How the fare is worked out

```
fare = ₹20 base + ₹6 per km + ₹0.50 per minute,   at least ₹30,   rounded to whole rupees
```

- **Distance and time** come from the real road route (OSRM). The time is never less than the trip would take
  at 22 km/h, a typical city speed, so traffic doesn't make fares unrealistically cheap.
- If the route service is down, CabZen estimates from the straight-line distance, so booking still works.
- **Example:** a 5 km trip. The time at city speed is 14 minutes, so the fare is 20 + (5 × 6) + (14 × 0.5) = **₹57**.
  The captain earns ₹45.60 and CabZen keeps ₹11.40 (20%).
- **Short trip:** 1 km and 3 minutes works out to ₹27.50, so the minimum fare of **₹30** applies.

The fare is calculated on the server when the ride is booked, so nobody can change the price in their browser.

## How captains are matched to a ride

A new request is shown to every captain who is:

- **approved**, not blocked, with an active bike;
- **online**, and not already on a ride;
- **within 5 km** of the pickup (straight-line distance);
- someone who has **not skipped** or cancelled this ride before.

Before a booking is accepted, CabZen counts the captains who match **and** sent their position in the last
2 minutes. If there are none, the booking is refused, so a passenger never waits for a captain who has
closed the app. Each captain sees up to 10 requests, nearest first. If two captains tap **Accept** at the same moment, the database lets exactly
one of them win; the other sees "Sorry, this ride was already taken or cancelled".

## Things that happen automatically

| Every | CabZen does this |
|---|---|
| 4 seconds | The passenger's and captain's pages check for changes (new request, captain assigned, ride started…) |
| 10 seconds | The admin dashboard refreshes |
| 15 seconds | An online captain's page sends their GPS position |
| 30 seconds | Requests nobody accepted within **3 minutes** become **EXPIRED** |
| 60 seconds | Online captains with no GPS for **10 minutes**, and not on a ride, are switched **offline** |

## How CabZen keeps things safe

- **Passwords** are stored only as BCrypt hashes, never as plain text.
- **Logins** use a session cookie plus a CSRF token, so other websites can't act on a user's behalf.
- **Every page and API checks the user's role.** A passenger can't open captain or admin screens, and so on.
- **Fares** are always worked out on the server.
- **Uploads** are checked by their actual file contents and limited to 2 MB.
- **No double booking.** A ride can only be accepted once, a captain can hold one ride at a time and a passenger
  can have one open booking. The database itself enforces this, not only the app.
- **Blocking works at once.** A blocked user's open sessions are ended immediately.

## Where to go next

- Run it and try a full ride: [GETTING_STARTED.md](GETTING_STARTED.md), sections 5 and 6.
- Find the code behind each step: [GETTING_STARTED.md](GETTING_STARTED.md), section 8.
- Technical design, every API endpoint and the database diagram: [PLAN.md](PLAN.md).
