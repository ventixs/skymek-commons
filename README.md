# skymek-commons

Shared code of the SkyMek Quarkus services (ms-data, ms-workorder, ms-inventory), published to GitHub Packages as `app.skymek:skymek-commons`.

## Use it

Each release attaches the jar, which downloads without a token (GitHub Packages' Maven registry always asks for one):

```groovy
repositories {
    ivy {
        url = uri('https://github.com/ventixs/skymek-commons/releases/download')
        patternLayout {
            artifact 'v[revision]/[artifact]-[revision].[ext]'
        }
        metadataSources {
            artifact()
        }
        content {
            includeGroup 'app.skymek'
        }
    }
}

dependencies {
    implementation 'app.skymek:skymek-commons:0.3.0'
}
```

## Support mode (`app.skymek.commons.support`)

A SYSTEM_ADMIN request carrying `X-Support-Session` runs as the target taller admin or owner, never as the
admin: `SupportIdentityAugmentor` replaces the identity and its token claims (`SupportIdentities`), and
`SupportModeFilter` answers 403 `{"error":"SUPPORT_MODE"}` to writes in a read-only session
(`SupportModeRules`). Any doubt about the session leaves an identity with no roles. Roles in a session:
`TALLER_ADMIN` or `VEHICLE_OWNER`, plus `support_readonly` («Solo ver») or `support_write` («Configurar»).

The service implements **`SupportGrantLookup`** as one `@ApplicationScoped` bean: the service that owns
the `support_session` table reads it, the others ask that service.

## Caller identity (`app.skymek.commons.security`)

- `TokenClaims`: reads `workshopId` (then `tallerId`), `ownerId`, the subject and text claims from the
  token, whether the mapper emits numbers or strings.
- `CallerIdentity` (request scoped): `current()` gives a `Caller` record whose taller counts only for
  TALLER_ADMIN or MECHANIC and whose owner counts only for VEHICLE_OWNER or OWNER; `tallerId()` and
  `ownerId()` give the raw claims; also `role()`, `systemAdmin()`, `subject()`, `email()`,
  `supportSessionId()`, `supportWrite()` and `requireTaller(id)`.
- `Roles`: the realm role names.

Nothing to implement.

## Price visibility for mechanics (`app.skymek.commons.pricing`)

`MechanicPriceFilter` redacts every response that carries money for a MECHANIC whose taller does not
allow it, and answers 403 on endpoints marked `@RequiresPriceVisibility`. `MechanicPriceVisibility`
decides, failing closed. The service implements two beans:

- **`MechanicPricePermissionLookup`**: the taller's `mechanicCanViewPrices`.
- **`PriceRedactor`**: which of its response types carry money and how to strip it.

Config: `skymek.pricing.permission-cache-ttl` (ISO-8601 duration, default `PT0S`, no cache). A service
that reads the permission over the network sets it, for example `PT60S`.

## Activity (`app.skymek.commons.activity`)

`ActivityPublishFilter` publishes every successful authenticated write to the Redis stream
`skymek:activity`, in the format `ActivityFields` builds; it never fails the request.

Config: **`skymek.activity.service`** (required), the service name written in each entry, for example
`ms-data`.

## Error body (`app.skymek.commons.error`)

`ErrorResponse(timestamp, status, errorCode, message, path, fieldErrors[])` with
`ErrorResponse.FieldError(field, message, rejectedValue)`; `fieldErrors` is never null. Exception mappers
stay in the services.

## Downstream calls (`app.skymek.commons.client`)

`AuthHeadersFactory` forwards `Authorization` and `X-Support-Session` to a REST client:
`@RegisterClientHeaders(AuthHeadersFactory.class)`.

## Hybrid cache (`app.skymek.commons.cache`)

`HybridCacheService` keeps an in-memory map (L1) in front of Redis (L2): `getOrCompute`, `get`, `put`,
`invalidate`, `invalidateAll` (the cache name may end in `*`), the non-blocking `peekL1`, `getStats`
(`CacheStats`) and `clearL1Cache`. Null values and empty collections or maps are never cached. Every Redis
key is `<key-prefix><cacheName>:<key>`.

Config:

| Key | Default | |
|---|---|---|
| **`skymek.cache.key-prefix`** | required | for example `cache:myservice:`; changing it orphans the keys already in Redis |
| `skymek.cache.l1.enabled` | `true` | |
| `skymek.cache.l1.max-size` | `1000` | entries per instance |
| `skymek.cache.l1.ttl-after-l2-hit` | `PT30M` | TTL of an L2 hit copied into L1 |
| `skymek.cache.l2.enabled` | `true` | |

The service provides the `ObjectMapper` (`quarkus-jackson`) and the Redis client.

## Release

Push a tag `vX.Y.Z`; the Publish workflow runs the tests and publishes that version.
