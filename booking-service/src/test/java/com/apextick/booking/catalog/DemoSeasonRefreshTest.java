package com.apextick.booking.catalog;

import com.apextick.booking.hold.HoldService;
import com.apextick.booking.security.CurrentUser;
import com.apextick.booking.seat.Seat;
import com.apextick.booking.seat.SeatRepository;
import com.apextick.booking.seat.SeatStatus;
import com.apextick.booking.support.IntegrationTest;
import com.apextick.booking.web.ConflictException;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The demo has to survive its own calendar.
 *
 * <p>Changeset 009 rolls the seeded season forward so the fixed 2026 dates in the 005 seed are
 * always ahead of now(); {@link CatalogSeedTest} pins that it did. But a test database is always
 * migrated seconds earlier, so that assertion is true by construction and can never see the
 * failure that matters: run once, 009 fixes only the day it ran, and about fourteen days later
 * the headline fixture kicks off and SalesWindow starts refusing every hold, order and payment
 * on it -- a catalog you can browse and nothing you can buy, with the suite still green.
 *
 * <p>So this test ages the database instead of waiting: it pushes the seeded season into the
 * past, checks the demo really is dead, then does what a restart does -- re-runs Liquibase --
 * and requires the catalog to be selling again. It also tampers with the recorded checksum
 * first, because the deployment that needs healing is precisely one migrated by an earlier
 * revision of this file, and a boot that fails checksum validation heals nothing.
 */
@IntegrationTest
class DemoSeasonRefreshTest {

    /** Every slug changeset 009 rolls forward. */
    private static final List<String> SEEDED_SLUGS = List.of(
            "india-pakistan-group-stage", "australia-england-super-8", "south-africa-new-zealand-super-8",
            "india-australia-semi-final", "world-cup-final", "mumbai-indians-chennai-super-kings",
            "bengaluru-kolkata-night", "gujarat-titans-rajasthan-royals", "chennai-mumbai-return",
            "qualifier-one", "arsenal-manchester-city", "liverpool-manchester-united",
            "manchester-city-tottenham", "newcastle-chelsea", "aston-villa-arsenal",
            "tottenham-chelsea-derby", "usa-paraguay-group-stage", "brazil-morocco-group-stage",
            "england-croatia-group-stage", "world-cup-final-2026");

    /** The earliest fixture, and the first thing a visitor clicks. */
    private static final String HEADLINE = "india-pakistan-group-stage";

    private static final String CHANGESET_ID = "009-01-demo-sales-windows";

    @Autowired SpringLiquibase liquibase;
    @Autowired EventRepository eventRepository;
    @Autowired SeatRepository seatRepository;
    @Autowired HoldService holdService;
    @Autowired JdbcClient jdbc;

    /** Winds the seeded season back, the way an untouched demo deployment ages into it. */
    private void ageTheSeededSeason() {
        jdbc.sql("""
                        UPDATE events
                           SET starts_at      = starts_at      - INTERVAL '200 days',
                               sales_start_at = sales_start_at - INTERVAL '200 days',
                               sales_end_at   = sales_end_at   - INTERVAL '200 days'
                         WHERE slug IN (:slugs)
                        """)
                .param("slugs", SEEDED_SLUGS)
                .update();
    }

    /** Kickoff times of the seeded season, earliest first. */
    private List<Instant> seasonInOrder() {
        return SEEDED_SLUGS.stream().map(s -> seeded(s).getStartsAt()).sorted().toList();
    }

    /** The gaps between consecutive fixtures -- the shape 009 must preserve. */
    private List<Duration> gaps(List<Instant> season) {
        return java.util.stream.IntStream.range(1, season.size())
                .mapToObj(i -> Duration.between(season.get(i - 1), season.get(i))).toList();
    }

    private String recordedChecksum() {
        return jdbc.sql("SELECT md5sum FROM databasechangelog WHERE id = :id")
                .param("id", CHANGESET_ID).query(String.class).single();
    }

    /** What a restart does. */
    private void boot() throws Exception {
        liquibase.afterPropertiesSet();
    }

    /**
     * Every class in the suite shares this database, so an aged season must never outlive this
     * one -- including when a test fails between winding the clock back and the restart that
     * heals it. The restart is the cleanup.
     */
    @AfterEach
    void leaveTheSeasonSellable() throws Exception {
        boot();
    }

    private Event seeded(String slug) {
        return eventRepository.findBySlug(slug).orElseThrow();
    }

    @Test
    void a_demo_whose_season_has_expired_is_selling_again_after_a_restart() throws Exception {
        // 005 seeds a ~148-day season, so 200 days back puts every fixture behind us
        ageTheSeededSeason();

        assertThatThrownBy(() -> SalesWindow.assertOpen(seeded(HEADLINE)))
                .isInstanceOf(ConflictException.class);
        assertThat(seeded(HEADLINE).getStartsAt()).isBefore(Instant.now());

        // nothing about the changelog is touched here: the changeset is already recorded with a
        // matching checksum, which is exactly the state a redeployed demo boots in. Only
        // runAlways brings it back.
        boot();

        Instant now = Instant.now();
        for (String slug : SEEDED_SLUGS) {
            Event e = seeded(slug);
            assertThat(e.getStartsAt()).as("%s starts", slug).isAfter(now);
            assertThat(e.getSalesStartAt()).as("%s sales open", slug).isBefore(now);
            assertThat(e.getSalesEndAt()).as("%s sales close", slug).isAfter(now);
            assertThatCode(() -> SalesWindow.assertOpen(e)).as("%s sells", slug).doesNotThrowAnyException();
        }

        // and the headline fixture is not just open on paper: a seat can actually be taken
        Long eventId = seeded(HEADLINE).getId();
        Long seatId = seatRepository.findAllForEventWithLayout(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.AVAILABLE)
                .map(Seat::getId).findFirst().orElseThrow();
        CurrentUser visitor = new CurrentUser("season-refresh-visitor", "refresher",
                "refresh@apextick.local", "Season Refresher", Set.of("user"));
        holdService.hold(HEADLINE, List.of(seatId), visitor);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
        holdService.releaseMine(HEADLINE, visitor);
    }

    /**
     * The deployment that needs healing is, by definition, one already migrated by an earlier
     * revision of this file -- and a changeset whose recorded checksum no longer matches makes
     * Liquibase refuse the whole update, so the service would not start at all and nothing
     * would be healed. runAlways is what lets that boot through: it re-runs the changeset and
     * exempts it from checksum validation, so the stale sum is rewritten instead of fatal.
     */
    @Test
    void a_database_migrated_by_an_earlier_revision_of_the_changeset_still_boots() throws Exception {
        String tampered = "8:00000000000000000000000000000000";
        jdbc.sql("UPDATE databasechangelog SET md5sum = :sum WHERE id = :id")
                .param("sum", tampered).param("id", CHANGESET_ID).update();

        assertThatCode(this::boot).doesNotThrowAnyException();

        assertThat(recordedChecksum()).isNotEqualTo(tampered);
    }

    /**
     * runAlways means the roll-forward runs on every boot, so it has to converge rather than
     * accumulate: the shift is recomputed from the season's own earliest fixture each time, so
     * booting twice must leave the season a fortnight out, not a month, and must not stretch
     * the gaps between fixtures that give the demo its group-stage-then-semis-then-final shape.
     */
    @Test
    void re_running_the_roll_forward_converges_instead_of_compounding() throws Exception {
        boot();
        List<Instant> once = seasonInOrder();

        boot();
        List<Instant> twice = seasonInOrder();

        assertThat(gaps(twice)).isEqualTo(gaps(once));
        Instant now = Instant.now();
        assertThat(twice.get(0))
                .isAfter(now.plus(Duration.ofDays(13)))
                .isBefore(now.plus(Duration.ofDays(15)));
    }
}
