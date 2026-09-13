# Runbook

For the production stack in `docker-compose.prod.yml`, on one server. Run the
commands from the checkout the stack runs from; `dc` below stands for
`docker compose -f docker-compose.prod.yml`.

## Deploy and roll back

A deploy is a set of image tags. CI publishes every commit on `main` and
`develop` as `sha-<commit>`, and `.env` names the three the server runs:

```bash
BOOKING_IMAGE=ghcr.io/kalanas210/apextick-booking:sha-<commit>
NOTIFICATION_IMAGE=ghcr.io/kalanas210/apextick-notification:sha-<commit>
FRONTEND_IMAGE=ghcr.io/kalanas210/apextick-frontend:sha-<commit>
```

1. Write down the tags `.env` holds now: they are the rollback.
2. Take a dump (`dc exec backup bash /scripts/backup.sh once`): a release whose
   migrations have run can only be rolled back past them by restoring one.
3. Set the new tags and check out the same commit, since Caddy, the realm import
   and the WSO2 config are read from the checkout. Then `dc pull && dc up -d`.
4. Watch `dc ps` until postgres, keycloak and rabbitmq report `healthy` and
   db-init has exited 0.

To roll back, put the old tags back, check out their commit and `dc up -d`
again. If the new release had migrated the database, restore the dump from step 2
first (below).

## Backups

The `backup` service dumps `apextick`, `apextick_notifications` and `keycloak`
every night at `BACKUP_AT` (UTC; 03:30 unless set) into `./backups`, and keeps
`BACKUP_KEEP_DAYS` days of them (7 unless set). A dump only gets its final name
once `pg_restore` has read it back, and old dumps are pruned only after a night
in which every database dumped cleanly.

- **Last night's:** `ls -lh backups/` and `dc logs --tail 20 backup`.
- **One now**, before anything risky: `dc exec backup bash /scripts/backup.sh once`.
- **Off the server:** the dumps share a disk with the data, so they cover a bad
  migration or a mistaken delete, not the loss of the server. Copy them
  elsewhere, e.g. `scp 'ubuntu@<host>:apextick/backups/*.dump' .` from your
  machine.

Not dumped: ticket PDFs in MinIO (rendered again on demand), WSO2's own
database (rebuilt by `scripts/wso2/setup.sh`) and RabbitMQ, whose queues hold
only what is in flight.

## Restore

### Check a dump without touching anything

```bash
dc exec backup bash /scripts/restore.sh /backups/apextick-<stamp>.dump apextick_restore_check
dc exec backup dropdb apextick_restore_check
```

`restore.sh` creates the new database, loads the dump into it and prints every
table with its row count. It refuses a name that already exists.

### Put a database back

1. Stop what writes to it: `dc stop booking-service` for `apextick`,
   `dc stop notification-service` for `apextick_notifications`, `dc stop keycloak`
   for `keycloak`.
2. Restore into a new name:

   ```bash
   dc exec backup bash /scripts/restore.sh /backups/apextick-<stamp>.dump apextick_restored
   ```

3. Swap the names:

   ```bash
   dc exec backup psql --dbname=postgres \
     --command='ALTER DATABASE apextick RENAME TO apextick_before_restore' \
     --command='ALTER DATABASE apextick_restored RENAME TO apextick'
   ```

4. Start the service again: `dc start booking-service`.
5. Once it looks right, `dc exec backup dropdb apextick_before_restore`.

In a drill on a development machine, three databases (one of them 307 MB and
2,000,000 rows) dumped and read back in 9 seconds, and the large one restored in
6. A restore loses everything since the dump it comes from, so up to a day of
bookings: take a dump by hand before anything risky.

## Keycloak

The realm and its accounts live in the `keycloak` database, so recreating the
container keeps them. The admin console is on a loopback-only port: open a
tunnel with `ssh -L 8180:localhost:8180 ubuntu@<host>`, then
http://localhost:8180/admin/. Its login needs the master realm's `frontendUrl`
pointed at the tunnel, once per database; the commands are next to the keycloak
service in `docker-compose.prod.yml`.

Roles: `scripts/grant-role.sh admin <user>` or `scripts/grant-role.sh scanner <user>`.

The console's own administrator is created from `KEYCLOAK_ADMIN` and
`KEYCLOAK_ADMIN_PASSWORD` only when the `keycloak` database is empty, so set a
real password before the first start; changing `.env` afterwards changes nothing.
Keycloak marks that account temporary: once in, create a permanent administrator
in the master realm and delete the temporary one. To change a password later,
change it in the console, then in `.env`, so the two agree.

## Checks

- `dc ps`: postgres, keycloak and rabbitmq have health checks, and db-init shows
  `Exited (0)`.
- The edge: `curl -sI https://<SERVER_IP>.nip.io/ | grep -i strict-transport-security`
  prints the header, and
  `curl -s -o /dev/null -w '%{http_code}\n' https://<SERVER_IP>.nip.io/realms/master/.well-known/openid-configuration`
  prints `404`.
- Refunds still owed: the refund-owed filter in the admin orders console, or the
  `apextick_refunds_owed` gauge with the observability profile up.
