package com.cabzen.repository;

import com.cabzen.entity.Booking;
import com.cabzen.entity.DriverProfile;
import com.cabzen.enums.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Every view of a booking shows passenger, driver, bike, payment and rating: load them in one query.

    @EntityGraph(attributePaths = {"passenger", "driver", "driver.user", "driver.vehicle", "payment", "rating"})
    Optional<Booking> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"passenger", "driver", "driver.user", "driver.vehicle", "payment", "rating"})
    Optional<Booking> findFirstByPassenger_IdAndStatusIn(Long passengerId, Collection<BookingStatus> statuses);

    @EntityGraph(attributePaths = {"passenger", "driver", "driver.user", "driver.vehicle", "payment", "rating"})
    Optional<Booking> findFirstByDriver_UserIdAndStatusIn(Long driverId, Collection<BookingStatus> statuses);

    @EntityGraph(attributePaths = {"passenger", "driver", "driver.user", "driver.vehicle", "payment", "rating"})
    List<Booking> findTop50ByPassenger_IdOrderByRequestedAtDesc(Long passengerId);

    @EntityGraph(attributePaths = {"passenger", "driver", "driver.user", "driver.vehicle", "payment", "rating"})
    List<Booking> findTop50ByDriver_UserIdOrderByRequestedAtDesc(Long driverId);

    // ponytail: admin lists show the latest 200 rows, add paging when the table outgrows that
    @EntityGraph(attributePaths = {"passenger", "driver", "driver.user", "driver.vehicle", "payment", "rating"})
    List<Booking> findTop200ByOrderByRequestedAtDesc();

    @EntityGraph(attributePaths = {"passenger", "driver", "driver.user", "driver.vehicle", "payment", "rating"})
    List<Booking> findTop200ByStatusOrderByRequestedAtDesc(BookingStatus status);

    @EntityGraph(attributePaths = {"passenger", "driver", "driver.user", "driver.vehicle", "payment", "rating"})
    List<Booking> findByStatusInOrderByRequestedAtDesc(Collection<BookingStatus> statuses);

    boolean existsByPassenger_IdAndStatusIn(Long passengerId, Collection<BookingStatus> statuses);

    boolean existsByDriver_UserIdAndStatusIn(Long driverId, Collection<BookingStatus> statuses);

    long countByStatusIn(Collection<BookingStatus> statuses);

    /**
     * Atomic "first driver wins": only succeeds while the booking is still REQUESTED.
     * Returns 1 for the winner, 0 for everyone else.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Booking b set b.driver = :driver, b.status = :accepted, b.acceptedAt = :now, b.version = b.version + 1
            where b.id = :id and b.status = :requested""")
    int accept(Long id, DriverProfile driver, Instant now, BookingStatus requested, BookingStatus accepted);

    @EntityGraph(attributePaths = {"passenger", "driver", "driver.user", "driver.vehicle", "payment", "rating"})
    List<Booking> findByIdIn(Collection<Long> ids);

    long countByDriver_UserIdAndStatus(Long driverId, BookingStatus status);

    /** Open requests near a driver, nearest pickup first, skipping ones this driver declined. */
    @Query(value = """
            select b.id from bookings b
            where b.status = 'REQUESTED'
              and distance_km(b.pickup_lat, b.pickup_lng, :lat, :lng) <= :radiusKm
              and not exists (select 1 from booking_rejections r where r.booking_id = b.id and r.driver_id = :driverId)
            order by distance_km(b.pickup_lat, b.pickup_lng, :lat, :lng)
            limit 10""", nativeQuery = true)
    List<Long> findOpenRequestIdsNear(Long driverId, double lat, double lng, double radiusKm);

    @Modifying
    @Query(value = "insert into booking_rejections (booking_id, driver_id) values (:bookingId, :driverId) on conflict do nothing",
            nativeQuery = true)
    void reject(Long bookingId, Long driverId);

    /** Expire unanswered requests and void their payments in one statement. */
    @Modifying
    @Query(value = """
            with expired as (
                update bookings set status = 'EXPIRED', version = version + 1
                where status = 'REQUESTED' and requested_at < :cutoff
                returning id)
            update payments set status = 'VOID' where booking_id in (select id from expired)""", nativeQuery = true)
    int expireRequestedBefore(Instant cutoff);

    @Query("select b.status as status, count(b) as total from Booking b group by b.status")
    List<StatusCount> countPerStatus();

    @Query("select b.passenger.id as id, count(b) as total from Booking b group by b.passenger.id")
    List<IdCount> countPerPassenger();

    @Query("select b.driver.userId as id, count(b) as total from Booking b where b.status = :status group by b.driver.userId")
    List<IdCount> countPerDriver(BookingStatus status);

    interface StatusCount {
        BookingStatus getStatus();
        long getTotal();
    }

    interface IdCount {
        Long getId();
        long getTotal();
    }
}
