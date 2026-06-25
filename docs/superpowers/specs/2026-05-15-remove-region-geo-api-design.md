# Remove Region, Replace Local Ref Data with External Geo APIs

## Goal

Remove the Region concept entirely. Replace the local Region/Country/City reference data tables and CRUD with runtime lookups against free external APIs. Store country and city as plain strings on Architect and Customer entities.

## Data Model

### Architect entity

- Remove: `regionId` (Long, NOT NULL)
- Change: `countryId` (Long) -> `country` (String, nullable)
- Change: `cityId` (Long) -> `city` (String, nullable)

### Customer entity

- Change: `countryId` (Long) -> `country` (String, nullable)
- Change: `cityId` (Long) -> `city` (String, nullable)

### ArchitectUsageDto

- Remove: `region` (String)
- Add: `country` (String)

### Database migration (new changeset)

1. Add `country` (varchar) and `city` (varchar) columns to `architect` and `customer` tables
2. Migrate existing data: join through old FK columns to populate new string columns
3. Drop old FK columns (`region_id`, `country_id`, `city_id` from `architect`; `country_id`, `city_id` from `customer`)
4. Drop tables: `city`, `country`, `region` (in that order due to FKs)

## Backend Changes

### Delete

- `Region.java`, `RegionRepository.java`
- `Country.java`, `CountryRepository.java`
- `City.java`, `CityRepository.java`
- `RefDataController.java`

### Modify

- `Architect.java`: remove `regionId` field, replace `countryId`/`cityId` with `country`/`city` (String)
- `Customer.java`: replace `countryId`/`cityId` with `country`/`city` (String)
- `ArchitectController.java`: no region-specific logic expected, but verify
- `UsageService.java`: remove `RegionRepository` dependency, remove region filtering, add country-based filtering (string match on `architect.country`), change DTO construction to use `architect.getCountry()`
- `UsageController.java`: replace `regionId` param with `country` (String) param
- `ArchitectUsageDto.java`: replace `region` with `country`

### Tests

- Update `ArchitectControllerTest.java`: use string country/city instead of FK IDs
- Update `AssignmentControllerTest.java`: adjust architect creation data
- Update `CustomerControllerTest.java`: use string country/city
- Update `UsageServiceTest.java`: remove region references, test country filtering

## Frontend Changes

### External APIs

- Countries: `GET https://restcountries.com/v3.1/all?fields=name` — returns all countries with common names. Cache in store after first fetch.
- Cities: `POST https://countriesnow.space/api/v0.1/countries/cities` with body `{ "country": "France" }` — returns cities for a given country. Fetch on demand when country is selected.

### Delete

- `regionApi` from `client.ts`
- `countryApi` from `client.ts`
- `cityApi` from `client.ts`
- Region/Country/City types from `types/index.ts`

### Modify

- **`types/index.ts`**: Architect gets `country?: string`, `city?: string` (remove `regionId`, `countryId`, `cityId`). Customer same. ArchitectUsage gets `country` instead of `region`.
- **`stores/refdata.ts`** -> rename to **`stores/geo.ts`**: new store that fetches countries from REST Countries API, caches them; provides `fetchCities(country)` that calls CountriesNow; exposes `countries` (string[]) and `citiesForCountry` (Map<string, string[]>).
- **`api/client.ts`**: remove region/country/city APIs. Update `usageApi.get` to accept `country` string instead of `regionId`.
- **`components/ArchitectForm.vue`**: replace region/country/city dropdowns with country dropdown (all countries) and city dropdown (loads when country selected). Both use string values directly.
- **`components/CustomerForm.vue`**: same pattern — country and city string dropdowns.
- **`views/ArchitectsView.vue`**: replace region filter with country filter dropdown. Remove region column from table, keep location display (now just reads `a.country` and `a.city` directly).
- **`views/UsageTimelineView.vue`**: replace region filter with country filter. Pass `country` string to usage store.
- **`components/TimelineGrid.vue`**: show `architect.country` instead of `architect.region`.
- **`views/AdminView.vue`**: remove Regions and Countries CRUD sections. Keep Cities section? No — cities come from API now. Remove the entire admin view or repurpose it. Since it only had ref data CRUD, remove the route and nav link.
- **`router/index.ts`**: remove admin route.
- **`components/AppLayout.vue`**: remove admin nav link.
- **`stores/usage.ts`**: change `fetchUsage` signature from `regionId?: number` to `country?: string`.

### Tests

- Update `ArchitectsView.spec.ts`: mock geo store instead of refdata, test country filter
- Update `CustomersProjectsView.spec.ts`: mock geo store, test country/city display
- Update `UsageTimelineView.spec.ts`: mock geo store, test country filter
