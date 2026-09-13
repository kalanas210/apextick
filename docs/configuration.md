# Configuration

Every setting the services read from the environment, with its default. Local
development runs on the defaults; `docker-compose.prod.yml` insists on the ones
this page marks as required, and `.env.example` walks through the credentials.
Durations are ISO-8601: `PT30S`, `PT5M`, `PT24H`.

## booking-service

### Connections

| Variable | Default | What it sets |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5440/apextick` | The database |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | `apextick`, `apextick` | Its login |
| `DB_POOL_SIZE`, `DB_POOL_MIN_IDLE` | `20`, `5` | The connection pool |
| `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT` | `localhost`, `5672` | The broker the outbox publishes to |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | `apextick`, `apextick` | Its login |
| `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT` | `localhost`, `6379` | Redis: hold expiry keys, rate limits, and the realtime fan-out |
| `JWT_ISSUER_URI` | `http://localhost:8180/realms/apextick` | The issuer every token must carry |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` | found through the issuer | Where signing keys are fetched, when that is not the public issuer (inside Docker) |
| `SPRING_JPA_SHOW_SQL` | `false` | Log every SQL statement; costly under load |
| `TOMCAT_MAX_THREADS` | `200` | Request threads |
| `TOMCAT_ACCEPT_COUNT`, `TOMCAT_MAX_CONNECTIONS` | `1000`, `10000` | How many connections a burst may queue, rather than being refused |

### What the deployment is

| Variable | Default | What it sets |
| --- | --- | --- |
| `LIQUIBASE_CONTEXTS` | `demo` | `demo` seeds the sample season and its shared account, and the site calls itself a demonstration; `prod` seeds nothing. **Required in production.** |
| `APP_PAYMENT_PROVIDER` | `mock` | `mock` takes no money; `stripe` takes test or live payments, as its keys decide. **Required in production.** |
| `APP_WEB_URL` | `http://localhost:3000` | The storefront's origin |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | The browser origins the API answers |

### Holds and orders

| Variable | Default | What it sets |
| --- | --- | --- |
| `APP_HOLD_DURATION` | `PT5M` | How long a hold lasts |
| `APP_HOLD_MAX_SEATS` | `8` | How many seats one person may hold for one event |
| `APP_HOLD_EXPIRY_TOLERANCE` | `PT2S` | How far past its deadline a hold must be before the fallback sweeper frees it; Redis expiry frees it on time |
| `APP_HOLD_SWEEPER_INTERVAL`, `APP_HOLD_SWEEPER_BATCH` | `PT30S`, `500` | How often that sweeper runs, and how many holds it frees at a time |
| `APP_ORDER_FEE_PERCENT` | `5` | The booking fee added to an order |
| `APP_ORDER_PAYMENT_WINDOW` | `PT10M` | How long an order waits for payment before it expires |
| `APP_ORDER_SWEEPER_INTERVAL` | `PT30S` | How often expired orders are closed and their seats released |
| `APP_RATE_LIMIT_ENABLED` | `true` | The per-person limits below |
| `APP_RATE_LIMIT_HOLD_LIMIT`, `APP_RATE_LIMIT_HOLD_WINDOW` | `30`, `PT1M` | Holds per person per window |
| `APP_RATE_LIMIT_ORDER_LIMIT`, `APP_RATE_LIMIT_ORDER_WINDOW` | `10`, `PT1M` | Orders per person per window |

### Payments and refunds

| Variable | Default | What it sets |
| --- | --- | --- |
| `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY`, `STRIPE_WEBHOOK_SECRET` | empty | Stripe's keys, read only when the provider is `stripe`. Test keys (`sk_test_…`) make the storefront say checkout runs in test mode. |
| `STRIPE_SUPPORTED_CURRENCIES` | empty: every currency | A comma-separated allow-list, e.g. `usd,gbp,inr` |
| `APP_REFUND_RETRY_INTERVAL` | `PT5M` | How often refunds the provider refused are asked for again |
| `APP_REFUND_RETRY_BACKOFF` | `PT10M` | How long a refused refund waits before it is asked for again |
| `APP_REFUND_RETRY_MAX_ATTEMPTS` | `12` | Refusals after which a refund waits for someone to retry it from the admin console |
| `APP_REFUND_RETRY_BATCH` | `50` | Refunds asked for in one pass |

### The outbox

| Variable | Default | What it sets |
| --- | --- | --- |
| `APP_OUTBOX_POLL_INTERVAL` | `PT1S` | How often committed events are relayed to RabbitMQ |
| `APP_OUTBOX_BATCH_SIZE` | `100` | Events relayed per poll |
| `APP_OUTBOX_MAX_ATTEMPTS` | `10` | Failed relays after which an event is marked dead |
| `APP_OUTBOX_CONFIRM_TIMEOUT` | `PT5S` | How long a relay waits for the broker's confirm |
| `APP_OUTBOX_REQUIRE_ROUTE` | `booking.confirmed,order.cancelled,payment.refunded,payment.failed,event.cancelled` | Events a queue must take before they count as published; others go out even when nothing is bound to them |

### Ticket PDFs

| Variable | Default | What it sets |
| --- | --- | --- |
| `S3_ENABLED` | `false` | Keep rendered PDFs in S3 or MinIO; `false` keeps them in memory |
| `S3_BUCKET`, `S3_REGION` | `apextick-tickets`, `us-east-1` | Where they go |
| `S3_ENDPOINT` | empty: AWS itself | A MinIO URL, locally |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | empty | Its credentials |
| `S3_PATH_STYLE` | `true` | Path-style bucket addressing, which MinIO needs |

## notification-service

| Variable | Default | What it sets |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5440/apextick_notifications` | Its own database |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | `apextick`, `apextick` | Its login |
| `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | as booking-service | The broker it consumes from |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` | `localhost`, `1025` | The SMTP server (Mailpit, locally) |
| `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | empty | The SMTP login |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` | `false` | Log in to the SMTP server |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | `false` | Upgrade the connection with STARTTLS |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED` | as STARTTLS_ENABLE | Refuse a server that does not offer STARTTLS, rather than send the login in plain text |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE` | `false` | Implicit TLS, for port 465 |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT`, `…_TIMEOUT`, `…_WRITETIMEOUT` | `10000` each | SMTP timeouts, in milliseconds |
| `MAIL_FROM` | `no-reply@apextick.local` | The From address |
| `APP_WEB_URL` | `http://localhost:3000` | The storefront's origin, for the links in e-mails |
| `APP_NOTIFICATIONS_SEAT_HELD_EMAIL` | `false` | Log a line for every seat held; no e-mail is sent either way |

The compose files fill the mail settings from `SMTP_*` in `.env`.

## frontend

| Variable | Default | What it sets |
| --- | --- | --- |
| `NEXT_PUBLIC_API_URL` | the page's own origin over HTTPS, otherwise port 8081 of the page's host | Where the browser finds the API |
| `API_INTERNAL_URL` | `NEXT_PUBLIC_API_URL`, then `http://localhost:8081` | Where server-rendered pages read the catalog, e.g. the service name inside Docker |

## docker-compose.prod.yml only

| Variable | Default | What it sets |
| --- | --- | --- |
| `SERVER_IP` | **required** | The server's public IP; the site is `https://<SERVER_IP>.nip.io` |
| `POSTGRES_DB` | **required** | The booking database's name |
| `BOOKING_IMAGE`, `NOTIFICATION_IMAGE`, `FRONTEND_IMAGE` | **required** | The exact `sha-<commit>` image tags to run |
| `API_UPSTREAM` | `wso2am:8280` | Where Caddy sends `/api`; `booking-service:8081` bypasses the gateway |
| `BACKUP_AT`, `BACKUP_KEEP_DAYS` | `03:30`, `7` | When the nightly dumps are taken (UTC), and how many days of them are kept |
| `WSO2_MEM_LIMIT`, `WSO2_JVM_MEM_OPTS` | `3g`, `-Xms1g -Xmx2g` | The gateway's memory |
| `DEMO_USER_EMAIL` | `kalana@apextick.local` | The seeded demo account's address |

The credentials (`POSTGRES_*`, `RABBITMQ_*`, `KEYCLOAK_ADMIN*`, `S3_*`,
`WSO2_ADMIN_PASSWORD`, `GRAFANA_PASSWORD`, `WSO2_KM_CLIENT_SECRET`,
`LOADTEST_CLIENT_SECRET`) have no defaults in production at all; `.env.example`
lists them.
